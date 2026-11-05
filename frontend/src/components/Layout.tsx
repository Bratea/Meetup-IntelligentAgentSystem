import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useRef } from 'react'
import { gsap, useGSAP } from '../hooks/gsap'
import './Layout.scss'

const navItems = [
  { to: '/chat', label: '💬 对话', exact: true },
  { to: '/agents', label: '🤖 智能体' },
  { to: '/knowledge', label: '📚 知识库' },
  { to: '/models', label: '🧠 模型' },
  { to: '/tools', label: '🔧 工具' },
]

export default function Layout() {
  const location = useLocation()
  const mainRef = useRef<HTMLElement>(null)

  // 路由切换过渡动画
  useGSAP(
    () => {
      if (!mainRef.current) return
      gsap.fromTo(
        mainRef.current.firstElementChild,
        { opacity: 0, y: 14 },
        { opacity: 1, y: 0, duration: 0.4, ease: 'power2.out' },
      )
    },
    { dependencies: [location.pathname] },
  )

  useGSAP(
    () => {
      const tl = gsap.timeline({ defaults: { ease: 'power3.out' } })

      tl.fromTo(
        '.layout__logo',
        { opacity: 0, scale: 0.6, rotation: -12 },
        { opacity: 1, scale: 1, rotation: 0, duration: 0.6, ease: 'back.out(1.8)' },
      )
        .fromTo(
          '.layout__title',
          { opacity: 0, x: -10 },
          { opacity: 1, x: 0, duration: 0.4 },
          '-=0.35',
        )
        .fromTo(
          '.layout__nav-item',
          { opacity: 0, x: -16 },
          { opacity: 1, x: 0, duration: 0.38, stagger: 0.06 },
          '-=0.2',
        )
        .fromTo(
          '.layout__footer',
          { opacity: 0 },
          { opacity: 1, duration: 0.4 },
          '-=0.1',
        )
    },
    { scope: undefined, dependencies: [] },
  )

  return (
    <div className="layout">
      <aside className="layout__sidebar">
        <div className="layout__brand">
          <div className="layout__logo">寻</div>
          <div className="layout__title">
            <div className="layout__title-main">寻晤智能agent系统</div>
            <div className="layout__title-sub">Xunwu Intelligent Agent</div>
          </div>
        </div>

        <nav className="layout__nav">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.exact}
              className={({ isActive }) =>
                `layout__nav-item${isActive ? ' layout__nav-item--active' : ''}`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="layout__footer">
          <span className="layout__footer-dot" />
          Spring AI · pgvector · Ollama
        </div>
      </aside>

      <main className="layout__main" ref={mainRef}>
        <Outlet />
      </main>
    </div>
  )
}
