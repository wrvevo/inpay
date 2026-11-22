FROM php:8.4-cli
WORKDIR /app
COPY . /app
RUN mkdir -p /app/data && chown -R www-data:www-data /app/data
USER www-data
EXPOSE 8099
CMD ["php", "-S", "0.0.0.0:8099", "-t", "public"]
