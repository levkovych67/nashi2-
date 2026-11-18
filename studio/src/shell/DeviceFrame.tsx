import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { DEVICES, frameScale, type DeviceKind } from './devices'

interface Props {
  device: DeviceKind
  identity: string
  children: ReactNode
}

export function DeviceFrame({ device, identity, children }: Props) {
  const container = useRef<HTMLDivElement>(null)
  const [scale, setScale] = useState(0)
  const { width, height } = DEVICES[device]

  useLayoutEffect(() => {
    const element = container.current
    if (!element) return
    const measure = () => setScale(frameScale(device, { width: element.clientWidth, height: element.clientHeight }))
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(element)
    return () => observer.disconnect()
  }, [device])

  return (
    <div className="device-frame" ref={container} data-device={device} data-identity={identity}>
      <div className="device-frame-bounds" style={{ width: width * scale, height: height * scale }}>
        <div className="device-frame-canvas" style={{ width, height, transform: 'scale(' + scale + ')' }}>
          {children}
        </div>
      </div>
    </div>
  )
}
