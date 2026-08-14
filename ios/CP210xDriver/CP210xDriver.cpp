#include <os/log.h>
#include <cstring>

#include <DriverKit/IOLib.h>
#include <DriverKit/IOUserServer.h>
#include <DriverKit/IOBufferMemoryDescriptor.h>
#include <DriverKit/OSDictionary.h>
#include <DriverKit/OSString.h>
#include <SerialDriverKit/SerialDriverKit.h>
#include <USBDriverKit/IOUSBHostInterface.h>

#define CP210xDriver_DECLARE_IVARS IOUSBHostInterface* usbInterface;
#include "CP210xDriver.h"

namespace {
constexpr uint32_t kDefaultBaudRate = 38400;
constexpr uint8_t kRequestOut = 0x41;
constexpr uint8_t kRequestIn = 0xC1;
constexpr uint8_t kInterfaceEnable = 0x00;
constexpr uint8_t kSetLineControl = 0x03;
constexpr uint8_t kSetBreak = 0x05;
constexpr uint8_t kSetModemHandshake = 0x07;
constexpr uint8_t kGetModemStatus = 0x08;
constexpr uint8_t kPurge = 0x12;
constexpr uint8_t kSetFlow = 0x13;
constexpr uint8_t kSetBaudRate = 0x1E;
constexpr uint16_t kUartEnable = 0x0001;
constexpr uint16_t kPurgeTx = 0x0004;
constexpr uint16_t kPurgeRx = 0x000A;
constexpr uint16_t kWriteDtr = 0x0100;
constexpr uint16_t kWriteRts = 0x0200;
constexpr uint16_t kDtrOn = 0x0001;
constexpr uint16_t kRtsOn = 0x0002;
constexpr uint32_t kControlTimeoutMs = 1000;

kern_return_t publishTTYIdentity(IOService* service)
{
	if (service == nullptr) return kIOReturnBadArgument;

	OSDictionary* properties = OSDictionary::withCapacity(3);
	OSString* streamType = OSString::withCString("IOSerialStream");
	OSString* baseName = OSString::withCString("rigweave");
	OSString* suffix = OSString::withCString("cp210x");
	if (properties == nullptr || streamType == nullptr || baseName == nullptr || suffix == nullptr) {
		if (properties != nullptr) properties->release();
		if (streamType != nullptr) streamType->release();
		if (baseName != nullptr) baseName->release();
		if (suffix != nullptr) suffix->release();
		return kIOReturnNoMemory;
	}

	const bool stored = properties->setObject("IOSerialBSDClientType", streamType) &&
		properties->setObject(kIOTTYBaseNameKey, baseName) &&
		properties->setObject(kIOTTYSuffixKey, suffix);
	streamType->release();
	baseName->release();
	suffix->release();
	if (!stored) {
		properties->release();
		return kIOReturnNoMemory;
	}

	(void)service->SetName("RigWeaveCP210x");
	const kern_return_t ret = service->SetProperties(properties);
	properties->release();
	return ret;
}

uint16_t lineControl(uint8_t dataBits, uint8_t halfStopBits, uint8_t parity)
{
	const uint16_t bits = static_cast<uint16_t>((dataBits < 5 || dataBits > 9 ? 8 : dataBits) << 8);
	const uint16_t stops = halfStopBits <= 2 ? 0x0000 : (halfStopBits == 3 ? 0x0001 : 0x0002);
	uint16_t parityBits = 0x0000;
	switch (parity) {
		case 1: parityBits = 0x0010; break;
		case 2: parityBits = 0x0020; break;
		case 3: parityBits = 0x0030; break;
		case 4: parityBits = 0x0040; break;
		default: break;
	}
	return bits | stops | parityBits;
}
}

kern_return_t
IMPL(CP210xDriver, Start)
{
	usbInterface = OSDynamicCast(IOUSBHostInterface, provider);
	if (usbInterface == nullptr) {
		os_log(OS_LOG_DEFAULT, "RigWeave CP210x provider is not IOUSBHostInterface");
		return kIOReturnUnsupported;
	}

	kern_return_t ret = Start(provider, SUPERDISPATCH);
	if (ret != kIOReturnSuccess) {
		os_log(OS_LOG_DEFAULT, "RigWeave CP210x Start super failed: 0x%08x", ret);
		return ret;
	}

	ret = publishTTYIdentity(this);
	if (ret != kIOReturnSuccess) {
		os_log(OS_LOG_DEFAULT, "RigWeave CP210x TTY identity failed: 0x%08x", ret);
		(void)Stop(provider, SUPERDISPATCH);
		usbInterface = nullptr;
		return ret;
	}

	ret = RegisterService();
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x matched VID 0x10C4 PID 0xEA60: 0x%08x", ret);
	if (ret != kIOReturnSuccess) {
		(void)Stop(provider, SUPERDISPATCH);
		usbInterface = nullptr;
	}
	return ret;
}

kern_return_t
IMPL(CP210xDriver, Stop)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x Stop");
	kern_return_t ret = Stop(provider, SUPERDISPATCH);
	usbInterface = nullptr;
	return ret;
}

kern_return_t
IMPL(CP210xDriver, HwActivate)
{
	kern_return_t ret = HwActivate(SUPERDISPATCH);
	if (ret != kIOReturnSuccess) {
		os_log(OS_LOG_DEFAULT, "RigWeave CP210x HwActivate super failed: 0x%08x", ret);
		return ret;
	}

	ret = writeValue(kInterfaceEnable, kUartEnable);
	if (ret != kIOReturnSuccess) {
		os_log(OS_LOG_DEFAULT, "RigWeave CP210x UART enable failed: 0x%08x", ret);
		HwDeactivate(SUPERDISPATCH);
	}
	return ret;
}

kern_return_t
IMPL(CP210xDriver, HwDeactivate)
{
	const kern_return_t hardwareRet = writeValue(kInterfaceEnable, 0);
	const kern_return_t superRet = HwDeactivate(SUPERDISPATCH);
	if (hardwareRet != kIOReturnSuccess) {
		os_log(OS_LOG_DEFAULT, "RigWeave CP210x UART disable failed: 0x%08x", hardwareRet);
		return hardwareRet;
	}
	return superRet;
}

kern_return_t
IMPL(CP210xDriver, HwResetFIFO)
{
	uint16_t value = 0;
	if (tx) value |= kPurgeTx;
	if (rx) value |= kPurgeRx;
	return value == 0 ? kIOReturnSuccess : writeValue(kPurge, value);
}

kern_return_t
IMPL(CP210xDriver, HwSendBreak)
{
	return writeValue(kSetBreak, sendBreak ? 1 : 0);
}

kern_return_t
IMPL(CP210xDriver, HwProgramUART)
{
	kern_return_t ret = HwProgramBaudRate(baudRate);
	if (ret != kIOReturnSuccess) return ret;
	return writeValue(kSetLineControl, lineControl(nDataBits, nHalfStopBits, parity));
}

kern_return_t
IMPL(CP210xDriver, HwProgramBaudRate)
{
	const uint32_t value = baudRate == 0 ? kDefaultBaudRate : baudRate;
	const uint8_t bytes[4] = {
		static_cast<uint8_t>(value), static_cast<uint8_t>(value >> 8),
		static_cast<uint8_t>(value >> 16), static_cast<uint8_t>(value >> 24)
	};
	return writeBytes(kSetBaudRate, bytes, sizeof(bytes));
}

kern_return_t
IMPL(CP210xDriver, HwProgramMCR)
{
	const uint16_t value = kWriteDtr | kWriteRts | (dtr ? kDtrOn : 0) | (rts ? kRtsOn : 0);
	return writeValue(kSetModemHandshake, value);
}

kern_return_t
IMPL(CP210xDriver, HwGetModemStatus)
{
	uint8_t status = 0;
	kern_return_t ret = readBytes(kGetModemStatus, &status, sizeof(status));
	if (ret != kIOReturnSuccess) return ret;
	if (cts != nullptr) *cts = (status & 0x10) != 0;
	if (dsr != nullptr) *dsr = (status & 0x20) != 0;
	if (ri != nullptr) *ri = (status & 0x40) != 0;
	if (dcd != nullptr) *dcd = (status & 0x80) != 0;
	return ret;
}

kern_return_t
IMPL(CP210xDriver, HwProgramLatencyTimer)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x latency=%{public}u", latency);
	return kIOReturnSuccess;
}

kern_return_t
IMPL(CP210xDriver, HwProgramFlowControl)
{
	uint8_t flow[16] = {};
	flow[8] = xon;
	flow[12] = xoff;
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x flow=%{public}u xon=%{public}u xoff=%{public}u", arg, xon, xoff);
	return writeBytes(kSetFlow, flow, sizeof(flow));
}

kern_return_t
CP210xDriver::writeValue(uint8_t request, uint16_t value)
{
	if (usbInterface == nullptr) return kIOReturnNotReady;
	uint16_t transferred = 0;
	return usbInterface->DeviceRequest(kRequestOut, request, value, 0, 0, nullptr, &transferred, kControlTimeoutMs);
}

kern_return_t
CP210xDriver::writeBytes(uint8_t request, const void* bytes, uint16_t length)
{
	if (usbInterface == nullptr || bytes == nullptr || length == 0) return kIOReturnBadArgument;
	IOBufferMemoryDescriptor* buffer = nullptr;
	kern_return_t ret = IOBufferMemoryDescriptor::Create(kIOMemoryDirectionOut, length, 0, &buffer);
	if (ret != kIOReturnSuccess || buffer == nullptr) return ret;
	IOAddressSegment range = {};
	ret = buffer->GetAddressRange(&range);
	if (ret == kIOReturnSuccess) {
		memcpy(reinterpret_cast<void*>(range.address), bytes, length);
		ret = buffer->SetLength(length);
	}
	uint16_t transferred = 0;
	if (ret == kIOReturnSuccess) {
		ret = usbInterface->DeviceRequest(kRequestOut, request, 0, 0, length, buffer, &transferred, kControlTimeoutMs);
	}
	buffer->release();
	return ret;
}

kern_return_t
CP210xDriver::readBytes(uint8_t request, void* bytes, uint16_t length)
{
	if (usbInterface == nullptr || bytes == nullptr || length == 0) return kIOReturnBadArgument;
	IOBufferMemoryDescriptor* buffer = nullptr;
	kern_return_t ret = IOBufferMemoryDescriptor::Create(kIOMemoryDirectionIn, length, 0, &buffer);
	if (ret != kIOReturnSuccess || buffer == nullptr) return ret;
	ret = buffer->SetLength(length);
	uint16_t transferred = 0;
	if (ret == kIOReturnSuccess) {
		ret = usbInterface->DeviceRequest(kRequestIn, request, 0, 0, length, buffer, &transferred, kControlTimeoutMs);
	}
	if (ret == kIOReturnSuccess && transferred >= length) {
		IOAddressSegment range = {};
		ret = buffer->GetAddressRange(&range);
		if (ret == kIOReturnSuccess) memcpy(bytes, reinterpret_cast<const void*>(range.address), length);
	}
	buffer->release();
	return ret;
}

void
CP210xDriver::handleRxPacket(uint8_t*& packet, uint32_t& size)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x RX bytes=%{public}u", size);
}

void
CP210xDriver::handleInterruptPacket(const uint8_t* packet, uint32_t size)
{
	os_log(OS_LOG_DEFAULT, "RigWeave CP210x interrupt bytes=%{public}u", size);
}
