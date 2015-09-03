FROM postgres

ADD init.sql /docker-entrypoint-initdb.d/
ADD identity.sql /docker-entrypoint-initdb.d/
ADD auth_entry.sql /docker-entrypoint-initdb.d/