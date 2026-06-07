FROM node:20-alpine AS builder
WORKDIR /app
COPY openapi.yaml openapi.yaml
# Copy lockfile first so the npm ci layer is only invalidated when dependencies change, not on every source edit
COPY frontend/package*.json frontend/
WORKDIR /app/frontend
# Run npm clean install
RUN npm ci
# node_modules is excluded via .dockerignore, so this won't stomp the npm ci installation
COPY frontend/ ./
RUN npm run codegen && npm run build

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/frontend/build build/
# Install only production deps — omits devDependencies (Vite, TypeScript, Playwright, etc.)
COPY --from=builder /app/frontend/package.json /app/frontend/package-lock.json ./
RUN npm ci --omit=dev
EXPOSE 3000
CMD ["node", "build"]
