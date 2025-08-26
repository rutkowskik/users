# 👤 User Manager Application  

A full-stack User Management Portal built with **Spring Boot** (backend) and **Angular** (frontend).  
The application provides complete user lifecycle management: registration, login, profile handling, role-based access, and full admin control over users.  

---

## 🚀 Tech Stack  

### Backend  
- Java 17  
- Spring Boot  
- Spring Security 6 with role-based authorization  
- JWT Authentication (tokens stored in LocalStorage)  
- Hibernate / JPA  
- PostgreSQL  

### Frontend  
- Angular  
- Bootstrap 5 (responsive UI)  
- Angular Notifier (user notifications)  

### Security  
- JWT for authentication, stored in LocalStorage  
- Spring Security protects backend endpoints with roles  
- Angular Route Guards prevent unauthorized navigation  

---

## ⚡ Features  

- ✅ User registration & login  
- ✅ Role-based access (`ROLE_USER`, `ROLE_ADMIN`, `ROLE_MANAGER`, …)  
- ✅ JWT authentication & session handling  
- ✅ Admin panel – add, edit, lock/unlock and delete users  
- ✅ User list with search and filters  
- ✅ User details modal (status, role, last login, join date)  
- ✅ Profile picture upload  
- ✅ Route guards in Angular (restricted access to views)  
- ✅ Password reset via email  

---

## 🎯 Roadmap  

- Pagination & sorting for user list  
- Audit logging (track who modified users)  
- External authentication support (e.g. Keycloak, OAuth2)  
