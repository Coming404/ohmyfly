// 目录读取产生真实的每日观测值；未打开应用的日期保留为空。
package catalog

import (
	"context"
	"database/sql"
	"time"
)

const trendSchema = `CREATE TABLE IF NOT EXISTS tower_daily_summary (
 day TEXT PRIMARY KEY, revision INTEGER NOT NULL, flights INTEGER NOT NULL, fuel_bytes INTEGER
);`

// DailySummary 中 nil 表示没有观测，0 表示确实观测到零。
type DailySummary struct {
	Day       string `json:"day"`
	Flights   *int64 `json:"flights"`
	FuelBytes *int64 `json:"fuelBytes"`
}

// 七个连续自然日始终占七个横轴位置；只读数据库中实际存在的记录。
func (s *Store) readTrend(ctx context.Context, tx *sql.Tx, now time.Time, result *Summary) (bool, error) {
	first := now.AddDate(0, 0, -6)
	result.Trend = make([]DailySummary, 7)
	for i := range result.Trend {
		result.Trend[i].Day = first.AddDate(0, 0, i).Format(time.DateOnly)
	}
	today := &result.Trend[6]
	rows, err := tx.QueryContext(ctx, `SELECT day,flights,fuel_bytes FROM tower_daily_summary WHERE day BETWEEN ? AND ? ORDER BY day`, result.Trend[0].Day, today.Day)
	if err != nil {
		return false, err
	}
	defer rows.Close()
	i := 0
	for rows.Next() {
		var d DailySummary
		if err := rows.Scan(&d.Day, &d.Flights, &d.FuelBytes); err != nil {
			return false, err
		}
		for i < len(result.Trend) && result.Trend[i].Day < d.Day {
			i++
		}
		if i < len(result.Trend) && result.Trend[i].Day == d.Day {
			result.Trend[i] = d
		}
	}
	if err := rows.Err(); err != nil {
		return false, err
	}
	unchanged := today.Flights != nil && *today.Flights == result.Flights && (today.FuelBytes != nil) == result.FuelReported
	if unchanged && result.FuelReported {
		unchanged = *today.FuelBytes == result.UsedTrafficBytes
	}
	today.Flights = &result.Flights
	if result.FuelReported {
		today.FuelBytes = &result.UsedTrafficBytes
	} else {
		today.FuelBytes = nil
	}
	return !unchanged, nil
}

// 聚合在只读连接完成，短写事务只保存仍是当前目录修订的快照。
func (s *Store) saveTrend(ctx context.Context, result Summary) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	last := result.Trend[6]
	_, err = tx.ExecContext(ctx, `INSERT INTO tower_daily_summary(day,revision,flights,fuel_bytes)
 SELECT ?,revision,?,? FROM catalog_meta WHERE id=1 AND revision=?
 ON CONFLICT(day) DO UPDATE SET revision=excluded.revision,flights=excluded.flights,fuel_bytes=excluded.fuel_bytes`, last.Day, result.Flights, last.FuelBytes, result.Revision)
	if err != nil {
		return err
	}
	if _, err = tx.ExecContext(ctx, `DELETE FROM tower_daily_summary WHERE day<?`, result.Trend[0].Day); err != nil {
		return err
	}
	return tx.Commit()
}
