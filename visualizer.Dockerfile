# Stage 1: Building the application
FROM node:22-alpine AS builder
WORKDIR /src
COPY visualizer /src

RUN yarn install
RUN yarn build

# Stage 2: Run the application
FROM node:22-alpine

# Set the working directory in the container
WORKDIR /src

# Copy the build output from the builder stage
COPY --from=builder /app/next.config.js ./
COPY --from=builder /app/public ./public
COPY --from=builder /app/.next ./.next
COPY --from=builder /app/node_modules ./node_modules

# Expose the port Next.js runs on
EXPOSE 3000

# Command to run the application
CMD ["npm", "start"]
