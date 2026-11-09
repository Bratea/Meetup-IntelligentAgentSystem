import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'
import './Markdown.scss'
// 使用 PrismLight 按需注册语言，避免把 300 个语言包全打进 bundle
import { PrismLight as SyntaxHighlighter } from 'react-syntax-highlighter'
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism'
import javascript from 'react-syntax-highlighter/dist/esm/languages/prism/javascript'
import typescript from 'react-syntax-highlighter/dist/esm/languages/prism/typescript'
import jsx from 'react-syntax-highlighter/dist/esm/languages/prism/jsx'
import tsx from 'react-syntax-highlighter/dist/esm/languages/prism/tsx'
import python from 'react-syntax-highlighter/dist/esm/languages/prism/python'
import java from 'react-syntax-highlighter/dist/esm/languages/prism/java'
import kotlin from 'react-syntax-highlighter/dist/esm/languages/prism/kotlin'
import go from 'react-syntax-highlighter/dist/esm/languages/prism/go'
import rust from 'react-syntax-highlighter/dist/esm/languages/prism/rust'
import c from 'react-syntax-highlighter/dist/esm/languages/prism/c'
import cpp from 'react-syntax-highlighter/dist/esm/languages/prism/cpp'
import csharp from 'react-syntax-highlighter/dist/esm/languages/prism/csharp'
import php from 'react-syntax-highlighter/dist/esm/languages/prism/php'
import ruby from 'react-syntax-highlighter/dist/esm/languages/prism/ruby'
import swift from 'react-syntax-highlighter/dist/esm/languages/prism/swift'
import bash from 'react-syntax-highlighter/dist/esm/languages/prism/bash'
import sql from 'react-syntax-highlighter/dist/esm/languages/prism/sql'
import json from 'react-syntax-highlighter/dist/esm/languages/prism/json'
import yaml from 'react-syntax-highlighter/dist/esm/languages/prism/yaml'
import markdownLang from 'react-syntax-highlighter/dist/esm/languages/prism/markdown'
import css from 'react-syntax-highlighter/dist/esm/languages/prism/css'
import scss from 'react-syntax-highlighter/dist/esm/languages/prism/scss'
import markup from 'react-syntax-highlighter/dist/esm/languages/prism/markup'
import ini from 'react-syntax-highlighter/dist/esm/languages/prism/ini'
import diff from 'react-syntax-highlighter/dist/esm/languages/prism/diff'
import docker from 'react-syntax-highlighter/dist/esm/languages/prism/docker'
import graphql from 'react-syntax-highlighter/dist/esm/languages/prism/graphql'
import lua from 'react-syntax-highlighter/dist/esm/languages/prism/lua'
import scala from 'react-syntax-highlighter/dist/esm/languages/prism/scala'
import dart from 'react-syntax-highlighter/dist/esm/languages/prism/dart'

// 注册常用语言，并补充常见别名
SyntaxHighlighter.registerLanguage('javascript', javascript)
SyntaxHighlighter.registerLanguage('js', javascript)
SyntaxHighlighter.registerLanguage('typescript', typescript)
SyntaxHighlighter.registerLanguage('ts', typescript)
SyntaxHighlighter.registerLanguage('jsx', jsx)
SyntaxHighlighter.registerLanguage('tsx', tsx)
SyntaxHighlighter.registerLanguage('python', python)
SyntaxHighlighter.registerLanguage('py', python)
SyntaxHighlighter.registerLanguage('java', java)
SyntaxHighlighter.registerLanguage('kotlin', kotlin)
SyntaxHighlighter.registerLanguage('kt', kotlin)
SyntaxHighlighter.registerLanguage('go', go)
SyntaxHighlighter.registerLanguage('golang', go)
SyntaxHighlighter.registerLanguage('rust', rust)
SyntaxHighlighter.registerLanguage('rs', rust)
SyntaxHighlighter.registerLanguage('c', c)
SyntaxHighlighter.registerLanguage('cpp', cpp)
SyntaxHighlighter.registerLanguage('c++', cpp)
SyntaxHighlighter.registerLanguage('csharp', csharp)
SyntaxHighlighter.registerLanguage('cs', csharp)
SyntaxHighlighter.registerLanguage('c#', csharp)
SyntaxHighlighter.registerLanguage('php', php)
SyntaxHighlighter.registerLanguage('ruby', ruby)
SyntaxHighlighter.registerLanguage('rb', ruby)
SyntaxHighlighter.registerLanguage('swift', swift)
SyntaxHighlighter.registerLanguage('bash', bash)
SyntaxHighlighter.registerLanguage('sh', bash)
SyntaxHighlighter.registerLanguage('shell', bash)
SyntaxHighlighter.registerLanguage('sql', sql)
SyntaxHighlighter.registerLanguage('json', json)
SyntaxHighlighter.registerLanguage('yaml', yaml)
SyntaxHighlighter.registerLanguage('yml', yaml)
SyntaxHighlighter.registerLanguage('markdown', markdownLang)
SyntaxHighlighter.registerLanguage('md', markdownLang)
SyntaxHighlighter.registerLanguage('css', css)
SyntaxHighlighter.registerLanguage('scss', scss)
SyntaxHighlighter.registerLanguage('markup', markup)
SyntaxHighlighter.registerLanguage('html', markup)
SyntaxHighlighter.registerLanguage('xml', markup)
SyntaxHighlighter.registerLanguage('svg', markup)
SyntaxHighlighter.registerLanguage('ini', ini)
SyntaxHighlighter.registerLanguage('toml', ini)
SyntaxHighlighter.registerLanguage('diff', diff)
SyntaxHighlighter.registerLanguage('docker', docker)
SyntaxHighlighter.registerLanguage('dockerfile', docker)
SyntaxHighlighter.registerLanguage('graphql', graphql)
SyntaxHighlighter.registerLanguage('gql', graphql)
SyntaxHighlighter.registerLanguage('lua', lua)
SyntaxHighlighter.registerLanguage('scala', scala)
SyntaxHighlighter.registerLanguage('dart', dart)

interface MarkdownProps {
  content: string
  className?: string
}

/**
 * Markdown 渲染组件
 *
 * <p>支持 GFM（表格 / 删除线 / 任务列表）与代码块语法高亮。
 * 代码块使用 prism 轻量构建 + oneDark 主题，只注册常用语言以控制体积。</p>
 */
const components: Components = {
  code({ className, children }) {
    const match = /language-([\w+#-]+)/.exec(className || '')
    const text = String(children ?? '')
    // react-markdown v10 不再透传 inline 标记，用 className 与是否含换行判断块级
    const isBlock = Boolean(match) || text.includes('\n')
    if (!isBlock) {
      return <code className="markdown__inline-code">{children}</code>
    }
    return (
      <SyntaxHighlighter
        style={oneDark}
        language={match ? match[1] : 'text'}
        PreTag="div"
        className="markdown__code-block"
        showLineNumbers={text.split('\n').length > 8}
      >
        {text.replace(/\n$/, '')}
      </SyntaxHighlighter>
    )
  },
  // 代码块已由 SyntaxHighlighter 渲染，去掉多余的 pre 包裹
  pre({ children }) {
    return <>{children}</>
  },
  a({ children, href }) {
    return (
      <a href={href} target="_blank" rel="noreferrer noopener">
        {children}
      </a>
    )
  },
}

export default function Markdown({ content, className }: MarkdownProps) {
  return (
    <div className={`markdown${className ? ` ${className}` : ''}`}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {content}
      </ReactMarkdown>
    </div>
  )
}
