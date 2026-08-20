ALTER table users
ADD constraint uk_user_email UNIQUE (email);