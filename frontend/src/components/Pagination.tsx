import './Pagination.scss'

interface PaginationProps {
  /** 当前页，从 1 开始 */
  page: number
  /** 每页条数 */
  size: number
  /** 总记录数 */
  total: number
  /** 总页数 */
  totalPages: number
  /** 可选的每页条数档位 */
  sizeOptions?: number[]
  onChange: (page: number, size: number) => void
}

/**
 * 通用分页条。
 *
 * 后端所有列表接口统一返回 `{records, total, page, size, totalPages}`，
 * 这里只负责展示与切换，具体取数由调用方完成。
 */
export default function Pagination({
  page,
  size,
  total,
  totalPages,
  sizeOptions = [10, 20, 50, 100],
  onChange,
}: PaginationProps) {
  if (total === 0) return null

  const go = (next: number) => {
    if (next < 1 || next > totalPages || next === page) return
    onChange(next, size)
  }

  return (
    <div className="pagination">
      <span className="pagination__info">
        共 {total} 条 · 第 {page} / {totalPages} 页
      </span>
      <div className="pagination__controls">
        {sizeOptions.length > 1 && (
          <select
            className="pagination__size"
            value={size}
            onChange={(e) => onChange(1, Number(e.target.value))}
          >
            {sizeOptions.map((option) => (
              <option key={option} value={option}>
                {option} 条/页
              </option>
            ))}
          </select>
        )}
        <button
          className="btn btn--sm btn--ghost"
          disabled={page <= 1}
          onClick={() => go(page - 1)}
        >
          上一页
        </button>
        <button
          className="btn btn--sm btn--ghost"
          disabled={page >= totalPages}
          onClick={() => go(page + 1)}
        >
          下一页
        </button>
      </div>
    </div>
  )
}
