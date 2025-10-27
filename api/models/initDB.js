import { dbPromise } from "../db.js";
import bcrypt from "bcrypt";

const init = async () => {
  const db = await dbPromise;

  await db.exec(`
    CREATE TABLE IF NOT EXISTS usuarios (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      usuario TEXT UNIQUE,
      password_hash TEXT,
      rol TEXT
    );

    CREATE TABLE IF NOT EXISTS solicitudes (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      usuario_id INTEGER,
      fecha_inicio TEXT,
      fecha_fin TEXT,
      motivo TEXT,
      estado TEXT DEFAULT 'pendiente',
      FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
    );
  `);

  const existingAdmin = await db.get("SELECT * FROM usuarios WHERE usuario = 'admin'");
  if (!existingAdmin) {
    const hash = await bcrypt.hash("admin123", 10);
    await db.run("INSERT INTO usuarios (usuario, password_hash, rol) VALUES (?, ?, ?)", "admin", hash, "admin");
    console.log("🧩 Usuario admin creado (admin / admin123)");
  }
};

init();
