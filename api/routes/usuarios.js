import express from "express";
import bcrypt from "bcryptjs";
import pool from "../db.js";

const router = express.Router();

// Crear tabla si no existe
(async () => {
  const query = `
    CREATE TABLE IF NOT EXISTS usuarios (
      id SERIAL PRIMARY KEY,
      nombre TEXT,
      email TEXT UNIQUE,
      password TEXT,
      rol TEXT DEFAULT 'empleado'
    );
  `;
  await pool.query(query);
})();

// Obtener todos los usuarios
router.get("/", async (req, res) => {
  try {
    const { rows } = await pool.query("SELECT id, nombre, email, rol FROM usuarios");
    res.json(rows);
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "Error al obtener usuarios" });
  }
});

// Registrar usuario
router.post("/registro", async (req, res) => {
  try {
    const { nombre, email, password, rol } = req.body;
    const hashed = await bcrypt.hash(password, 10);

    await pool.query(
      "INSERT INTO usuarios (nombre, email, password, rol) VALUES ($1, $2, $3, $4)",
      [nombre, email, hashed, rol || "empleado"]
    );

    res.json({ mensaje: "Usuario registrado correctamente" });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "Error al registrar usuario" });
  }
});

// Login
router.post("/login", async (req, res) => {
  try {
    const { email, password } = req.body;
    const { rows } = await pool.query("SELECT * FROM usuarios WHERE email = $1", [email]);

    if (rows.length === 0) return res.status(401).json({ error: "Credenciales inválidas" });

    const user = rows[0];
    const isValid = await bcrypt.compare(password, user.password);

    if (!isValid) return res.status(401).json({ error: "Credenciales inválidas" });

    res.json({
      mensaje: "Inicio de sesión exitoso",
      usuario: { id: user.id, nombre: user.nombre, rol: user.rol },
    });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "Error en el servidor" });
  }
});

export default router;
