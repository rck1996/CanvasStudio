#include <jni.h>
#include <android/log.h>
#include <android/trace.h>
#include <vulkan/vulkan.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <cmath>
#include <vector>

static const uint32_t brush_comp_spv[] =
#include "brush_comp.spv.h"
;

namespace {
constexpr const char* TAG = "CanvasStudioVulkan";

struct Buffer {
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkDeviceSize capacity = 0;
    void* mapped = nullptr;
};

struct PushConstants {
    int32_t width;
    int32_t height;
    int32_t tileLeft;
    int32_t tileTop;
    int32_t dabCount;
    int32_t selectionCount;
    int32_t flags;
    float grainDepth;
    int32_t dabIndex;
    int32_t dispatchLeft;
    int32_t dispatchTop;
    int32_t reserved;
};

class Renderer {
public:
    bool initialize() {
        std::lock_guard<std::mutex> guard(lock_);
        VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
        app.pApplicationName = "Canvas Studio";
        app.applicationVersion = VK_MAKE_VERSION(2, 3, 0);
        app.pEngineName = "CanvasStudioTileRaster";
        app.engineVersion = VK_MAKE_VERSION(1, 0, 0);
        app.apiVersion = VK_API_VERSION_1_1;
        VkInstanceCreateInfo instanceInfo{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
        instanceInfo.pApplicationInfo = &app;
        if (vkCreateInstance(&instanceInfo, nullptr, &instance_) != VK_SUCCESS) return false;

        uint32_t deviceCount = 0;
        if (vkEnumeratePhysicalDevices(instance_, &deviceCount, nullptr) != VK_SUCCESS || deviceCount == 0) return false;
        std::vector<VkPhysicalDevice> devices(deviceCount);
        vkEnumeratePhysicalDevices(instance_, &deviceCount, devices.data());
        for (VkPhysicalDevice candidate : devices) {
            uint32_t familyCount = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &familyCount, nullptr);
            std::vector<VkQueueFamilyProperties> families(familyCount);
            vkGetPhysicalDeviceQueueFamilyProperties(candidate, &familyCount, families.data());
            for (uint32_t index = 0; index < familyCount; ++index) {
                if ((families[index].queueFlags & VK_QUEUE_COMPUTE_BIT) != 0) {
                    physicalDevice_ = candidate;
                    queueFamily_ = index;
                    break;
                }
            }
            if (physicalDevice_ != VK_NULL_HANDLE) break;
        }
        if (physicalDevice_ == VK_NULL_HANDLE) return false;
        vkGetPhysicalDeviceMemoryProperties(physicalDevice_, &memoryProperties_);
        vkGetPhysicalDeviceProperties(physicalDevice_, &deviceProperties_);

        float priority = 1.f;
        VkDeviceQueueCreateInfo queueInfo{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
        queueInfo.queueFamilyIndex = queueFamily_;
        queueInfo.queueCount = 1;
        queueInfo.pQueuePriorities = &priority;
        VkDeviceCreateInfo deviceInfo{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
        deviceInfo.queueCreateInfoCount = 1;
        deviceInfo.pQueueCreateInfos = &queueInfo;
        if (vkCreateDevice(physicalDevice_, &deviceInfo, nullptr, &device_) != VK_SUCCESS) return false;
        vkGetDeviceQueue(device_, queueFamily_, 0, &queue_);

        std::array<VkDescriptorSetLayoutBinding, 3> bindings{};
        for (uint32_t index = 0; index < bindings.size(); ++index) {
            bindings[index].binding = index;
            bindings[index].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            bindings[index].descriptorCount = 1;
            bindings[index].stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        }
        VkDescriptorSetLayoutCreateInfo layoutInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO};
        layoutInfo.bindingCount = static_cast<uint32_t>(bindings.size());
        layoutInfo.pBindings = bindings.data();
        if (vkCreateDescriptorSetLayout(device_, &layoutInfo, nullptr, &descriptorLayout_) != VK_SUCCESS) return false;

        VkPushConstantRange pushRange{};
        pushRange.stageFlags = VK_SHADER_STAGE_COMPUTE_BIT;
        pushRange.size = sizeof(PushConstants);
        VkPipelineLayoutCreateInfo pipelineLayoutInfo{VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO};
        pipelineLayoutInfo.setLayoutCount = 1;
        pipelineLayoutInfo.pSetLayouts = &descriptorLayout_;
        pipelineLayoutInfo.pushConstantRangeCount = 1;
        pipelineLayoutInfo.pPushConstantRanges = &pushRange;
        if (vkCreatePipelineLayout(device_, &pipelineLayoutInfo, nullptr, &pipelineLayout_) != VK_SUCCESS) return false;

        VkShaderModuleCreateInfo shaderInfo{VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO};
        shaderInfo.codeSize = sizeof(brush_comp_spv);
        shaderInfo.pCode = brush_comp_spv;
        VkShaderModule shader = VK_NULL_HANDLE;
        if (vkCreateShaderModule(device_, &shaderInfo, nullptr, &shader) != VK_SUCCESS) return false;
        VkPipelineShaderStageCreateInfo stage{VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO};
        stage.stage = VK_SHADER_STAGE_COMPUTE_BIT;
        stage.module = shader;
        stage.pName = "main";
        VkComputePipelineCreateInfo pipelineInfo{VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO};
        pipelineInfo.stage = stage;
        pipelineInfo.layout = pipelineLayout_;
        VkResult pipelineResult = vkCreateComputePipelines(device_, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr, &pipeline_);
        vkDestroyShaderModule(device_, shader, nullptr);
        if (pipelineResult != VK_SUCCESS) return false;

        VkDescriptorPoolSize poolSize{VK_DESCRIPTOR_TYPE_STORAGE_BUFFER, 3};
        VkDescriptorPoolCreateInfo poolInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO};
        poolInfo.maxSets = 1;
        poolInfo.poolSizeCount = 1;
        poolInfo.pPoolSizes = &poolSize;
        if (vkCreateDescriptorPool(device_, &poolInfo, nullptr, &descriptorPool_) != VK_SUCCESS) return false;
        VkDescriptorSetAllocateInfo allocateInfo{VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO};
        allocateInfo.descriptorPool = descriptorPool_;
        allocateInfo.descriptorSetCount = 1;
        allocateInfo.pSetLayouts = &descriptorLayout_;
        if (vkAllocateDescriptorSets(device_, &allocateInfo, &descriptorSet_) != VK_SUCCESS) return false;

        VkCommandPoolCreateInfo commandPoolInfo{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
        commandPoolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        commandPoolInfo.queueFamilyIndex = queueFamily_;
        if (vkCreateCommandPool(device_, &commandPoolInfo, nullptr, &commandPool_) != VK_SUCCESS) return false;
        VkCommandBufferAllocateInfo commandInfo{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
        commandInfo.commandPool = commandPool_;
        commandInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        commandInfo.commandBufferCount = 1;
        if (vkAllocateCommandBuffers(device_, &commandInfo, &commandBuffer_) != VK_SUCCESS) return false;

        VkQueryPoolCreateInfo queryInfo{VK_STRUCTURE_TYPE_QUERY_POOL_CREATE_INFO};
        queryInfo.queryType = VK_QUERY_TYPE_TIMESTAMP;
        queryInfo.queryCount = 2;
        vkCreateQueryPool(device_, &queryInfo, nullptr, &queryPool_);
        initialized_ = true;
        return true;
    }

    ~Renderer() {
        std::lock_guard<std::mutex> guard(lock_);
        if (device_ != VK_NULL_HANDLE) vkDeviceWaitIdle(device_);
        destroyBuffer(pixelBuffer_);
        destroyBuffer(dabBuffer_);
        destroyBuffer(selectionBuffer_);
        if (queryPool_) vkDestroyQueryPool(device_, queryPool_, nullptr);
        if (commandPool_) vkDestroyCommandPool(device_, commandPool_, nullptr);
        if (descriptorPool_) vkDestroyDescriptorPool(device_, descriptorPool_, nullptr);
        if (pipeline_) vkDestroyPipeline(device_, pipeline_, nullptr);
        if (pipelineLayout_) vkDestroyPipelineLayout(device_, pipelineLayout_, nullptr);
        if (descriptorLayout_) vkDestroyDescriptorSetLayout(device_, descriptorLayout_, nullptr);
        if (device_) vkDestroyDevice(device_, nullptr);
        if (instance_) vkDestroyInstance(instance_, nullptr);
    }

    bool render(
        uint32_t* pixels, int width, int height, int tileLeft, int tileTop,
        const float* dabs, int dabFloatCount, const float* selection, int selectionFloatCount,
        int flags, float grainDepth, int64_t* timings
    ) {
        std::lock_guard<std::mutex> guard(lock_);
        if (!initialized_ || pixels == nullptr || dabs == nullptr || width <= 0 || height <= 0 || dabFloatCount < 12) return false;
        const auto totalStart = Clock::now();
        const VkDeviceSize pixelBytes = static_cast<VkDeviceSize>(width) * height * sizeof(uint32_t);
        const VkDeviceSize dabBytes = static_cast<VkDeviceSize>(dabFloatCount) * sizeof(float);
        const VkDeviceSize selectionBytes = std::max<VkDeviceSize>(sizeof(float) * 2, static_cast<VkDeviceSize>(selectionFloatCount) * sizeof(float));
        ATrace_beginSection("CanvasStudio.Vulkan.Upload");
        if (!ensureBuffer(pixelBuffer_, pixelBytes) || !ensureBuffer(dabBuffer_, dabBytes) || !ensureBuffer(selectionBuffer_, selectionBytes)) {
            ATrace_endSection(); return false;
        }
        std::memcpy(pixelBuffer_.mapped, pixels, static_cast<size_t>(pixelBytes));
        std::memcpy(dabBuffer_.mapped, dabs, static_cast<size_t>(dabBytes));
        if (selection != nullptr && selectionFloatCount > 0) {
            std::memcpy(selectionBuffer_.mapped, selection, static_cast<size_t>(selectionFloatCount) * sizeof(float));
        } else {
            std::memset(selectionBuffer_.mapped, 0, sizeof(float) * 2);
        }
        timings[0] = nanosSince(totalStart);
        ATrace_endSection();

        std::array<VkDescriptorBufferInfo, 3> infos{{
            {pixelBuffer_.buffer, 0, pixelBytes}, {dabBuffer_.buffer, 0, dabBytes},
            {selectionBuffer_.buffer, 0, selectionBytes},
        }};
        std::array<VkWriteDescriptorSet, 3> writes{};
        for (uint32_t index = 0; index < writes.size(); ++index) {
            writes[index].sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
            writes[index].dstSet = descriptorSet_;
            writes[index].dstBinding = index;
            writes[index].descriptorCount = 1;
            writes[index].descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            writes[index].pBufferInfo = &infos[index];
        }
        vkUpdateDescriptorSets(device_, static_cast<uint32_t>(writes.size()), writes.data(), 0, nullptr);

        ATrace_beginSection("CanvasStudio.Vulkan.Raster");
        vkResetCommandBuffer(commandBuffer_, 0);
        VkCommandBufferBeginInfo begin{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        begin.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
        if (vkBeginCommandBuffer(commandBuffer_, &begin) != VK_SUCCESS) { ATrace_endSection(); return false; }
        if (queryPool_) {
            vkCmdResetQueryPool(commandBuffer_, queryPool_, 0, 2);
            vkCmdWriteTimestamp(commandBuffer_, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, queryPool_, 0);
        }
        vkCmdBindPipeline(commandBuffer_, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline_);
        vkCmdBindDescriptorSets(commandBuffer_, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout_, 0, 1, &descriptorSet_, 0, nullptr);
        PushConstants push{width, height, tileLeft, tileTop, dabFloatCount / 12, selectionFloatCount / 2, flags, grainDepth, 0, 0, 0, 0};
        for (int dabIndex = 0; dabIndex < push.dabCount; ++dabIndex) {
            const int base = dabIndex * 12;
            const float centerX = dabs[base] - tileLeft;
            const float centerY = dabs[base + 1] - tileTop;
            const float radius = std::max(dabs[base + 2], dabs[base + 3]) + 2.f;
            const int left = std::max(0, static_cast<int>(std::floor(centerX - radius)));
            const int top = std::max(0, static_cast<int>(std::floor(centerY - radius)));
            const int right = std::min(width, static_cast<int>(std::ceil(centerX + radius)));
            const int bottom = std::min(height, static_cast<int>(std::ceil(centerY + radius)));
            if (right <= left || bottom <= top) continue;
            push.dabIndex = dabIndex;
            push.dispatchLeft = left;
            push.dispatchTop = top;
            vkCmdPushConstants(commandBuffer_, pipelineLayout_, VK_SHADER_STAGE_COMPUTE_BIT, 0, sizeof(push), &push);
            vkCmdDispatch(commandBuffer_, static_cast<uint32_t>((right - left + 7) / 8), static_cast<uint32_t>((bottom - top + 7) / 8), 1);
            if (dabIndex + 1 < push.dabCount) {
                VkMemoryBarrier dabBarrier{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
                dabBarrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
                dabBarrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
                vkCmdPipelineBarrier(commandBuffer_, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0, 1, &dabBarrier, 0, nullptr, 0, nullptr);
            }
        }
        if (queryPool_) vkCmdWriteTimestamp(commandBuffer_, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, queryPool_, 1);
        VkMemoryBarrier barrier{VK_STRUCTURE_TYPE_MEMORY_BARRIER};
        barrier.srcAccessMask = VK_ACCESS_SHADER_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_HOST_READ_BIT;
        vkCmdPipelineBarrier(commandBuffer_, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_HOST_BIT, 0, 1, &barrier, 0, nullptr, 0, nullptr);
        if (vkEndCommandBuffer(commandBuffer_) != VK_SUCCESS) { ATrace_endSection(); return false; }
        timings[1] = nanosSince(totalStart) - timings[0];
        ATrace_endSection();

        ATrace_beginSection("CanvasStudio.Vulkan.Submit");
        const auto submitStart = Clock::now();
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &commandBuffer_;
        if (vkQueueSubmit(queue_, 1, &submit, VK_NULL_HANDLE) != VK_SUCCESS) { ATrace_endSection(); return false; }
        timings[2] = nanosSince(submitStart);
        ATrace_endSection();

        ATrace_beginSection("CanvasStudio.Vulkan.Wait");
        const auto waitStart = Clock::now();
        if (vkQueueWaitIdle(queue_) != VK_SUCCESS) { ATrace_endSection(); return false; }
        timings[3] = nanosSince(waitStart);
        ATrace_endSection();

        if (queryPool_) {
            uint64_t timestamps[2]{};
            if (vkGetQueryPoolResults(device_, queryPool_, 0, 2, sizeof(timestamps), timestamps, sizeof(uint64_t), VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WAIT_BIT) == VK_SUCCESS) {
                timings[5] = static_cast<int64_t>((timestamps[1] - timestamps[0]) * deviceProperties_.limits.timestampPeriod);
            }
        }
        ATrace_beginSection("CanvasStudio.Vulkan.Readback");
        const auto readStart = Clock::now();
        std::memcpy(pixels, pixelBuffer_.mapped, static_cast<size_t>(pixelBytes));
        timings[4] = nanosSince(readStart);
        ATrace_endSection();
        return true;
    }

    const char* deviceName() const { return deviceProperties_.deviceName; }
    int64_t allocatedBytes() const { return static_cast<int64_t>(pixelBuffer_.capacity + dabBuffer_.capacity + selectionBuffer_.capacity); }

private:
    using Clock = std::chrono::steady_clock;
    static int64_t nanosSince(Clock::time_point start) {
        return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now() - start).count();
    }

    uint32_t memoryType(uint32_t bits) const {
        const VkMemoryPropertyFlags wanted = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
        for (uint32_t index = 0; index < memoryProperties_.memoryTypeCount; ++index) {
            if ((bits & (1u << index)) && (memoryProperties_.memoryTypes[index].propertyFlags & wanted) == wanted) return index;
        }
        return UINT32_MAX;
    }

    bool ensureBuffer(Buffer& target, VkDeviceSize required) {
        if (target.capacity >= required) return true;
        destroyBuffer(target);
        VkDeviceSize capacity = 4096;
        while (capacity < required) capacity *= 2;
        VkBufferCreateInfo bufferInfo{VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO};
        bufferInfo.size = capacity;
        bufferInfo.usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
        bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vkCreateBuffer(device_, &bufferInfo, nullptr, &target.buffer) != VK_SUCCESS) return false;
        VkMemoryRequirements requirements{};
        vkGetBufferMemoryRequirements(device_, target.buffer, &requirements);
        uint32_t type = memoryType(requirements.memoryTypeBits);
        if (type == UINT32_MAX) return false;
        VkMemoryAllocateInfo allocation{VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO};
        allocation.allocationSize = requirements.size;
        allocation.memoryTypeIndex = type;
        if (vkAllocateMemory(device_, &allocation, nullptr, &target.memory) != VK_SUCCESS) return false;
        if (vkBindBufferMemory(device_, target.buffer, target.memory, 0) != VK_SUCCESS) return false;
        if (vkMapMemory(device_, target.memory, 0, capacity, 0, &target.mapped) != VK_SUCCESS) return false;
        target.capacity = capacity;
        return true;
    }

    void destroyBuffer(Buffer& target) {
        if (device_ == VK_NULL_HANDLE) return;
        if (target.mapped && target.memory) vkUnmapMemory(device_, target.memory);
        if (target.buffer) vkDestroyBuffer(device_, target.buffer, nullptr);
        if (target.memory) vkFreeMemory(device_, target.memory, nullptr);
        target = {};
    }

    std::mutex lock_;
    bool initialized_ = false;
    VkInstance instance_ = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice_ = VK_NULL_HANDLE;
    VkPhysicalDeviceProperties deviceProperties_{};
    VkPhysicalDeviceMemoryProperties memoryProperties_{};
    uint32_t queueFamily_ = 0;
    VkDevice device_ = VK_NULL_HANDLE;
    VkQueue queue_ = VK_NULL_HANDLE;
    VkDescriptorSetLayout descriptorLayout_ = VK_NULL_HANDLE;
    VkPipelineLayout pipelineLayout_ = VK_NULL_HANDLE;
    VkPipeline pipeline_ = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool_ = VK_NULL_HANDLE;
    VkDescriptorSet descriptorSet_ = VK_NULL_HANDLE;
    VkCommandPool commandPool_ = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffer_ = VK_NULL_HANDLE;
    VkQueryPool queryPool_ = VK_NULL_HANDLE;
    Buffer pixelBuffer_;
    Buffer dabBuffer_;
    Buffer selectionBuffer_;
};

Renderer* fromHandle(jlong handle) { return reinterpret_cast<Renderer*>(handle); }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_orbyte_canvasstudio_drawing_raster_VulkanNativeBridge_nativeCreate(JNIEnv*, jclass) {
    auto* renderer = new Renderer();
    if (!renderer->initialize()) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "Vulkan initialization failed");
        delete renderer;
        return 0;
    }
    __android_log_print(ANDROID_LOG_INFO, TAG, "Using Vulkan device: %s", renderer->deviceName());
    return reinterpret_cast<jlong>(renderer);
}

extern "C" JNIEXPORT void JNICALL
Java_com_orbyte_canvasstudio_drawing_raster_VulkanNativeBridge_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    delete fromHandle(handle);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_orbyte_canvasstudio_drawing_raster_VulkanNativeBridge_nativeRender(
    JNIEnv* env, jclass, jlong handle, jintArray pixelArray, jint width, jint height,
    jint tileLeft, jint tileTop, jfloatArray dabArray, jfloatArray selectionArray,
    jint flags, jfloat grainDepth, jlongArray timingArray
) {
    Renderer* renderer = fromHandle(handle);
    if (!renderer || !pixelArray || !dabArray || !timingArray) return JNI_FALSE;
    jint* pixels = env->GetIntArrayElements(pixelArray, nullptr);
    jfloat* dabs = env->GetFloatArrayElements(dabArray, nullptr);
    jfloat* selection = selectionArray ? env->GetFloatArrayElements(selectionArray, nullptr) : nullptr;
    jlong timings[6]{};
    bool success = renderer->render(
        reinterpret_cast<uint32_t*>(pixels), width, height, tileLeft, tileTop,
        dabs, env->GetArrayLength(dabArray), selection,
        selectionArray ? env->GetArrayLength(selectionArray) : 0, flags, grainDepth,
        reinterpret_cast<int64_t*>(timings)
    );
    if (selectionArray && selection) env->ReleaseFloatArrayElements(selectionArray, selection, JNI_ABORT);
    env->ReleaseFloatArrayElements(dabArray, dabs, JNI_ABORT);
    env->ReleaseIntArrayElements(pixelArray, pixels, success ? 0 : JNI_ABORT);
    if (success) env->SetLongArrayRegion(timingArray, 0, 6, timings);
    return success ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_orbyte_canvasstudio_drawing_raster_VulkanNativeBridge_nativeDeviceName(JNIEnv* env, jclass, jlong handle) {
    Renderer* renderer = fromHandle(handle);
    return env->NewStringUTF(renderer ? renderer->deviceName() : "Unavailable");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_orbyte_canvasstudio_drawing_raster_VulkanNativeBridge_nativeAllocatedBytes(JNIEnv*, jclass, jlong handle) {
    Renderer* renderer = fromHandle(handle);
    return renderer ? renderer->allocatedBytes() : 0;
}
