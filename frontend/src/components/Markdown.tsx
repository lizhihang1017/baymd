import ReactMarkdown from 'react-markdown'

/** Markdown 渲染 — 支持加粗/列表/代码块/引用,禁用原始 HTML(安全) */
export default function Markdown({ content, onDark }: { content: string; onDark?: boolean }) {
  return (
    <div className={`markdown-body text-sm leading-relaxed ${onDark ? "text-white/90" : "text-u-text"}`}>
      <ReactMarkdown
        components={{
          // 段落
          p: ({ children }) => <p className="my-1.5">{children}</p>,
          // 加粗
          strong: ({ children }) => <strong className={`font-semibold ${onDark ? "text-white" : "text-u-text"}`}>{children}</strong>,
          // 列表
          ul: ({ children }) => <ul className="my-1.5 pl-5 list-disc space-y-0.5">{children}</ul>,
          ol: ({ children }) => <ol className="my-1.5 pl-5 list-decimal space-y-0.5">{children}</ol>,
          li: ({ children }) => <li className="leading-relaxed">{children}</li>,
          // 标题
          h1: ({ children }) => <h1 className="text-lg font-semibold my-2">{children}</h1>,
          h2: ({ children }) => <h2 className="text-base font-semibold my-2">{children}</h2>,
          h3: ({ children }) => <h3 className="text-sm font-semibold my-1.5">{children}</h3>,
          // 行内代码
          code: ({ children, className }) => (
            <code className={`${className || ''} px-1 py-0.5 rounded bg-u-bg text-[0.85em] font-mono`}>
              {children}
            </code>
          ),
          // 引用
          blockquote: ({ children }) => (
            <blockquote className={`my-2 pl-3 border-l-2 border-u-amber/50 italic ${onDark ? "text-white/70" : "text-u-muted"}`}>
              {children}
            </blockquote>
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  )
}
