-- Import data into APP_USER.
-- Assumes app_users.csv has header: email,fullname,age,password
COPY APP_USER(EMAIL, FULLNAME, AGE, PASSWORD)
    FROM '/docker-entrypoint-initdb.d/app_users.csv'
    DELIMITER ','
    CSV HEADER;
