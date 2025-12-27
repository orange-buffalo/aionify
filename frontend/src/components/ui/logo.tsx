import React from "react"

interface LogoProps {
  className?: string
  width?: number
  height?: number
  onClick?: () => void
}

export function Logo({ className = "", width = 400, height = 96, onClick }: LogoProps) {
  const clickableProps = onClick ? { onClick, style: { cursor: 'pointer' } } : {}

  return (
    <div className={className} {...clickableProps}>
      <svg
        width={width}
        height={height}
        viewBox="0 0 600 140"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        {/* Clock Icon (matches favicon, for dark bg) */}
        <g>
          {/* Outer blue circle */}
          <circle cx="70" cy="70" r="44" fill="#76aad5"/>
          {/* Inner clock face (dark accent) */}
          <circle cx="70" cy="70" r="38" fill="#353648"/>
          {/* Clock hands (10:10) */}
          <line x1="70" y1="70" x2="57" y2="38" stroke="#37a2ea" strokeWidth="6" strokeLinecap="round"/>
          <line x1="70" y1="70" x2="89" y2="38" stroke="#4797d9" strokeWidth="6" strokeLinecap="round"/>
          {/* Center dot */}
          <circle cx="70" cy="70" r="5" fill="#FFF"/>
        </g>

        {/* Aionify wordmark for dark background, 84px font, vertically centered */}
        <text x="150" y="100" fontFamily="Segoe UI, Arial, sans-serif" fontSize="84" fontWeight="bold" fill="#FFF" letterSpacing="2">Aion</text>
        <text x="333" y="100" fontFamily="Segoe UI, Arial, sans-serif" fontSize="84" fontWeight="bold" fill="#37a2ea">ify</text>
      </svg>
    </div>
  )
}
