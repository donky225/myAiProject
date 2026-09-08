{{/* 공통 라벨 */}}
{{- define "ai-platform.labels" -}}
app.kubernetes.io/part-of: local-ai-platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}
