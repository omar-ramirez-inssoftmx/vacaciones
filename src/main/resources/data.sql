INSERT INTO users (username, password, dias_disponibles, role)
VALUES ('admin', '{noop}admin', 30, 'ROLE_ADMIN');

INSERT INTO users (username, password, dias_disponibles, role)
VALUES ('colab', '{noop}colab', 12, 'ROLE_COLABORADOR');