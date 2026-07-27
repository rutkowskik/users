# 🏗️ Etap 1: Build Angular App
FROM node:20-alpine AS build

WORKDIR /usr/local/app

# Kopiujemy pliki zależności
COPY ./ /usr/local/app

# Instalujemy zależności
RUN npm install --legacy-peer-deps

# Budowa aplikacji w trybie produkcyjnym
RUN npm run build -- --configuration production

# 🌐 Etap 2: Serwowanie przez Nginx
FROM nginx:1.25-alpine

# Usuwamy domyślną konfigurację
RUN rm /etc/nginx/conf.d/default.conf

# Kopiujemy naszą konfigurację Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Kopiujemy zbudowany frontend z poprzedniego etapu
COPY --from=build /usr/local/app/dist/usersapp/browser /usr/share/nginx/html

# Otwieramy port 80
EXPOSE 80

# Uruchamiamy Nginx
CMD ["nginx", "-g", "daemon off;"]
