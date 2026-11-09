import { useEffect, useState } from 'react'
import { agentApi } from '../api/agent'
import {
  clearAccessToken,
  getAccessToken,
  setAccessToken,
  NetworkError,
} from '../api/request'
import './AuthGate.scss'

type GateState = 'checking' | 'ok' | 'need-token'

interface AuthGateProps {
  children: React.ReactNode
}

/**
 * 访问令牌门禁。
 *
 * 后端配置了 `XUNWU_AUTH_TOKEN` 时，所有 `/api/**` 都需要
 * `Authorization: Bearer <token>`。这里负责：
 *
 * 1. 进入时用一次轻量请求探测后端是否需要令牌；
 * 2. 需要时显示令牌输入框，验证通过后写入 localStorage；
 * 3. 401 时清掉本地令牌（`request.ts` 已处理），回到输入态。
 *
 * 后端未配置令牌时探测直接通过，用户无感知。
 */
export default function AuthGate({ children }: AuthGateProps) {
  const [state, setState] = useState<GateState>('checking')
  const [input, setInput] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    let cancelled = false
    // 不带令牌探测：后端要求鉴权时返回 401，不需要时正常返回
    agentApi
      .list(1, 1)
      .then(() => {
        if (!cancelled) setState('ok')
      })
      .catch((err) => {
        if (cancelled) return
        if (err instanceof NetworkError && err.status === 401) {
          setState('need-token')
        } else {
          // 后端不可达或其他错误：交给各页面自己的错误态展示
          setState('ok')
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    const candidate = input.trim()
    if (!candidate) {
      setError('请输入访问令牌')
      return
    }
    setSubmitting(true)
    setError('')
    setAccessToken(candidate)
    try {
      await agentApi.list(1, 1)
      setState('ok')
    } catch (err) {
      clearAccessToken()
      setError(
        err instanceof NetworkError && err.status === 401
          ? '令牌无效，请重新输入'
          : `验证失败：${(err as Error).message}`,
      )
    } finally {
      setSubmitting(false)
    }
  }

  if (state === 'checking') {
    return (
      <div className="auth-gate auth-gate--checking">
        <div className="auth-gate__spinner" />
        <p className="auth-gate__hint">正在连接服务…</p>
      </div>
    )
  }

  if (state === 'ok') return <>{children}</>

  return (
    <div className="auth-gate">
      <form className="auth-gate__card" onSubmit={submit}>
        <h1 className="auth-gate__title">需要访问令牌</h1>
        <p className="auth-gate__hint">
          该服务已启用接口鉴权。请输入管理员分配的访问令牌（Bearer Token）。
        </p>
        <input
          className="auth-gate__input"
          type="password"
          value={input}
          autoFocus
          placeholder="请输入访问令牌"
          onChange={(e) => setInput(e.target.value)}
        />
        {error && <div className="auth-gate__error">{error}</div>}
        <button className="btn btn--primary auth-gate__submit" disabled={submitting}>
          {submitting ? '验证中…' : '进入系统'}
        </button>
        {getAccessToken() && (
          <button
            type="button"
            className="btn btn--ghost auth-gate__clear"
            onClick={() => {
              clearAccessToken()
              setInput('')
            }}
          >
            清除已保存的令牌
          </button>
        )}
      </form>
    </div>
  )
}
