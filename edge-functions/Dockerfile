FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY tsconfig.json ./
COPY src/ ./src/
RUN npm run build

FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY --from=builder /app/dist ./dist

# APNs key: set APNS_KEY_CONTENT env var with base64-encoded .p8 content
# OR mount the key file and set APNS_KEY_PATH (default: ./certs/AuthKey.p8)

EXPOSE 3000

CMD ["node", "dist/index.js"]
