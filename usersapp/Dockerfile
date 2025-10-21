# 🏗️ Etap 1: Build Angular App
FROM node:20-alpine AS build

WORKDIR /app

# Kopiujemy package.json i package-lock.json
COPY package*.json ./

# Instalujemy zależności
RUN npm install --legacy-peer-deps

# Kopiujemy źródła i budujemy aplikację produkcyjną
COPY . .
RUN npm run build --prod

# 🌐 Etap 2: Serwowanie przez Nginx
FROM nginx:1.25-alpine

# Usuwamy domyślną konfigurację
RUN rm /etc/nginx/conf.d/default.conf

# Kopiujemy naszą konfigurację Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Kopiujemy zbudowany frontend z poprzedniego etapu
COPY --from=build /app/dist/usersapp /usr/share/nginx/html

# Otwieramy port 80
EXPOSE 80

# Uruchamiamy Nginx
CMD ["nginx", "-g", "daemon off;"]
