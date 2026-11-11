package flight

// Flight 保存归一化配置与来源；ContentHash 不包含显示名称和来源元数据。
type Flight struct {
	Name         string
	Type         string
	Server       string
	Port         int
	Fields       map[string]any
	RawInput     string
	SourceFormat string
	FromBase64   bool
}
