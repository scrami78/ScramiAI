#include <cuda_runtime.h>

__global__ void sai_scale(float* data, int n, float scale) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < n) data[i] *= scale;
}

extern "C" cudaError_t sai_scale_cuda(float* data, int n, float scale) {
    if (!data || n <= 0) return cudaErrorInvalidValue;
    int blocks = (n + 255) / 256;
    sai_scale<<<blocks, 256>>>(data, n, scale);
    return cudaGetLastError();
}
