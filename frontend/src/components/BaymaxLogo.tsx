/** 大白(Baymax)风格医疗机器人 Logo — 圆润、温和、治愈 */
export default function BaymaxLogo({ size = 40, className = '' }: { size?: number; className?: string }) {
  return (
    <svg width={size} height={size} viewBox="0 0 48 48" fill="none" className={className} aria-hidden>
      {/* 头 */}
      <circle cx="24" cy="20" r="15" fill="white" stroke="#E2E8F0" strokeWidth="1.5" />
      {/* 眼睛(两条弧线 — 大白标志性眯眯眼) */}
      <path d="M17 18 Q 19.5 15.5 22 18" stroke="#1E293B" strokeWidth="2" strokeLinecap="round" fill="none" />
      <path d="M26 18 Q 28.5 15.5 31 18" stroke="#1E293B" strokeWidth="2" strokeLinecap="round" fill="none" />
      {/* 嘴(微笑弧) */}
      <path d="M21.5 23.5 Q 24 25.5 26.5 23.5" stroke="#94A3B8" strokeWidth="1.2" strokeLinecap="round" fill="none" />
      {/* 脖子 */}
      <rect x="21" y="33" width="6" height="4" rx="2" fill="#E2E8F0" />
      {/* 身体 */}
      <rect x="14" y="35" width="20" height="9" rx="4.5" fill="white" stroke="#E2E8F0" strokeWidth="1.5" />
      {/* 肚脐(红色医疗十字) */}
      <rect x="22.5" y="37.5" width="3" height="4" rx="0.75" fill="#DC2626" />
      <rect x="22" y="38" width="4" height="3" rx="0.75" fill="#DC2626" />
    </svg>
  )
}
