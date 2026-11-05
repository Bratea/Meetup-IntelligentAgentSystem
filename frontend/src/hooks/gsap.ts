import { gsap } from 'gsap'
import { useGSAP } from '@gsap/react'
import { useRef } from 'react'

export { gsap, useGSAP }

/**
 * 通用入场动画预设
 */
export const presets = {
  /** 标题淡入上移 */
  header: {
    from: { opacity: 0, y: -16 },
    to: { opacity: 1, y: 0, duration: 0.5, ease: 'power3.out' as const },
  },
  /** 卡片入场 */
  card: {
    from: { opacity: 0, y: 18, scale: 0.98 },
    to: {
      opacity: 1,
      y: 0,
      scale: 1,
      duration: 0.5,
      ease: 'power3.out' as const,
      stagger: 0.06,
    },
  },
  /** 消息气泡 */
  message: {
    from: { opacity: 0, y: 14, scale: 0.97 },
    to: {
      opacity: 1,
      y: 0,
      scale: 1,
      duration: 0.38,
      ease: 'power2.out' as const,
    },
  },
  /** 侧边栏元素 */
  sidebarItem: {
    from: { opacity: 0, x: -14 },
    to: {
      opacity: 1,
      x: 0,
      duration: 0.4,
      ease: 'power3.out' as const,
      stagger: 0.05,
    },
  },
}

/**
 * 页面入场动画 Hook：标题 + 卡片
 */
export function usePageIntro() {
  const root = useRef<HTMLDivElement>(null)

  useGSAP(
    () => {
      gsap.fromTo('.page__header', presets.header.from, presets.header.to)
      gsap.fromTo('.card', presets.card.from, { ...presets.card.to, stagger: 0.06 })
    },
    { scope: root, dependencies: [] },
  )

  return root
}
