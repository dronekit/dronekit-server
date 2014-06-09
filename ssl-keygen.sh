# Generates SSL key and CSR for api.3drobotics.com
# Run this on the server
openssl req -new -newkey rsa:2048 -nodes -out api_3drobotics_com.csr -keyout api_3drobotics_com.key -subj "/C=US/ST=California/L=San Diego/O=3D Robotics Inc./CN=api.3drobotics.com"
