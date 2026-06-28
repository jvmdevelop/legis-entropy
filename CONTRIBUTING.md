
# Requirements: 
### 1. when u starting develop u need create certificates in certs with openSSL command to start local developing. 
```shell
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout ip.key -out ip.crt
```

### 2. almost u need setup .env file to local development. U have to choose docker-compose.prod.yml and docker-compose.dev.yml

### 3. almost u need setup application properties to local developpment. 
