@echo off

docker build -f Dockerfile -t trdat04/cdp-backend-api:3.9 .
docker push trdat04/cdp-backend-api:3.9

pause
