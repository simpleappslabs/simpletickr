{{- define "simpletickr.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "simpletickr.backend.fullname" -}}
{{- printf "%s-backend" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "simpletickr.backend.selectorLabels" -}}
app.kubernetes.io/name: {{ printf "%s-backend" .Release.Name | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "simpletickr.backend.labels" -}}
helm.sh/chart: {{ include "simpletickr.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{ include "simpletickr.backend.selectorLabels" . }}
{{- end }}

{{- define "simpletickr.frontend.fullname" -}}
{{- printf "%s-frontend" .Release.Name | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "simpletickr.frontend.selectorLabels" -}}
app.kubernetes.io/name: {{ printf "%s-frontend" .Release.Name | trunc 63 | trimSuffix "-" }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "simpletickr.frontend.labels" -}}
helm.sh/chart: {{ include "simpletickr.chart" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{ include "simpletickr.frontend.selectorLabels" . }}
{{- end }}

{{- define "simpletickr.backend.image" -}}
{{- $tag := .Values.image.tag | default .Chart.AppVersion }}
{{- printf "%s/%s/%s:%s" .Values.image.registry .Values.image.project .Values.backend.image.repository $tag }}
{{- end }}

{{- define "simpletickr.frontend.image" -}}
{{- $tag := .Values.image.tag | default .Chart.AppVersion }}
{{- printf "%s/%s/%s:%s" .Values.image.registry .Values.image.project .Values.frontend.image.repository $tag }}
{{- end }}

{{/* Resolves the DB host: bitnami postgresql service name when subchart is enabled */}}
{{- define "simpletickr.backend.dbHost" -}}
{{- if .Values.postgresql.enabled -}}
{{- printf "%s-postgresql" .Release.Name }}
{{- else -}}
{{- required "backend.db.host is required when postgresql.enabled is false" .Values.backend.db.host }}
{{- end }}
{{- end }}

{{/* Resolves the API base URL seen by the frontend */}}
{{- define "simpletickr.frontend.apiBaseUrl" -}}
{{- if .Values.frontend.apiBaseUrl -}}
{{- .Values.frontend.apiBaseUrl }}
{{- else if .Values.ingress.enabled -}}
{{- .Values.ingress.apiPath }}
{{- else -}}
{{- printf "http://%s:%d%s" (include "simpletickr.backend.fullname" .) (.Values.backend.service.port | int) .Values.ingress.apiPath }}
{{- end }}
{{- end }}

{{/* Resolves the backend's health-check path, kept in sync with ingress.apiPath */}}
{{- define "simpletickr.backend.healthPath" -}}
{{- printf "%s%s" (.Values.ingress.apiPath | trimSuffix "/") "/actuator/health" }}
{{- end }}
