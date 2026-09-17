FROM node:20-alpine

WORKDIR /app

# Copy package configuration
COPY package*.json ./

# Install npm dependencies inside container
RUN npm install

# Copy source code
COPY . .

EXPOSE 3000

# Run Vite dev server bound to 0.0.0.0 for container networking
CMD ["npm", "run", "dev"]
