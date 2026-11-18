export type DeviceKind = 'mobile' | 'desktop'

export const DEVICES = {
  mobile: { width: 390, height: 844 },
  mobileMin: { width: 360, height: 640 },
  desktop: { width: 1440, height: 900 },
  desktopMin: { width: 1024, height: 768 },
} as const

export function frameScale(device: DeviceKind, container: { width: number; height: number }): number {
  const { width, height } = DEVICES[device]
  return Math.max(0, Math.min(1, container.width / width, container.height / height))
}
