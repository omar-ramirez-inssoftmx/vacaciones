    import express from "express";
import bcrypt from "bcryptjs";
import sqlite3 from "sqlite3";
import { open } from "sqlite";

const router = express.Router();

// Abrir conexión con la base de datos SQLite
const dbPromise = open({
  filename: "./api/db.sqlite",
  driver: sqlite3.Database,
});

// ✅ Crear tabla si no existe
(async () => {
  const db = await dbPromise;
  await db.exec(`
    CREATE TABLE IF NOT EXISTS usuarios (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      nombre TEXT,
      email TEXT UNIQUE,
      password TEXT,
      rol TEXT DEFAULT 'empleado'
    )
  `);
})();

// ✅ Obtener todos los usuarios
router.get("/", async (req, res) => {
  try {
    const db = await dbPromise;
    const usuarios = await db.all("SELECT id, nombre, email, rol FROM usuarios");
    res.json(usuarios);
  } catch (error) {
    console.error("Error al obtener usuarios:", error);
    res.status(500).json({ error: "Error al obtener usuarios" });
  }
});

// ✅ Registrar un nuevo usuario
router.post("/registro", async (req, res) => {
  try {
    const { nombre, email, password, rol } = req.body;
    const db = await dbPromise;

    const hashed = await bcrypt.hash(password, 10);
    await db.run(
      "INSERT INTO usuarios (nombre, email, password, rol) VALUES (?, ?, ?, ?)",
      [nombre, email, hashed, rol || "empleado"]
    );

    res.json({ mensaje: "Usuario registrado correctamente" });
  } catch (error) {
    console.error("Error al registrar usuario:", error);
    res.status(500).json({ error: "Error al registrar usuario" });
  }
});

// ✅ Login de usuario
router.post("/login", async (req, res) => {
  try {
    const { email, password } = req.body;
    const db = await dbPromise;

    const user = await db.get("SELECT * FROM usuarios WHERE email = ?", [email]);

    if (!user) {
      return res.status(401).json({ error: "Credenciales inválidas" });
    }

    const isValid = await bcrypt.compare(password, user.password);
    if (!isValid) {
      return res.status(401).json({ error: "Credenciales inválidas" });
    }

    res.json({
      mensaje: "Inicio de sesión exitoso",
      usuario: { id: user.id, nombre: user.nombre, rol: user.rol },
    });
  } catch (error) {
    console.error("Error en login:", error);
    res.status(500).json({ error: "Error en el servidor" });
  }
});

export default router;