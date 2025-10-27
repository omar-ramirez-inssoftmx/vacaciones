import express from "express";
import jwt from "jsonwebtoken";
import bcrypt from "bcrypt";
import dotenv from "dotenv";
import { dbPromise } from "../db.js";
import { authMiddleware } from "../middleware/authMiddleware.js";

dotenv.config();
const router = express.Router();

router.post("/login", async (req, res) => {
  const { usuario, password } = req.body;
  const db = await dbPromise;
  const user = await db.get("SELECT * FROM usuarios WHERE usuario = ?", usuario);
  if (!user) return res.status(400).json({ error: "Usuario no encontrado" });
  const valid = await bcrypt.compare(password, user.password_hash);
  if (!valid) return res.status(401).json({ error: "Contraseña incorrecta" });
  const token = jwt.sign({ id: user.id, rol: user.rol }, process.env.JWT_SECRET || "devsecret", { expiresIn: "2h" });
  res.json({ token, rol: user.rol });
});

router.post("/usuarios", authMiddleware, async (req, res) => {
  try {
    const { rol } = req.user;
    if (rol !== "admin") return res.status(403).json({ error: "No autorizado" });
    const { usuario, password, nuevoRol } = req.body;
    if (!usuario || !password || !nuevoRol) return res.status(400).json({ error: "Faltan campos" });
    const db = await dbPromise;
    const existe = await db.get("SELECT id FROM usuarios WHERE usuario = ?", usuario);
    if (existe) return res.status(400).json({ error: "El usuario ya existe" });
    const hash = await bcrypt.hash(password, 10);
    await db.run("INSERT INTO usuarios (usuario, password_hash, rol) VALUES (?, ?, ?)", usuario, hash, nuevoRol);
    res.json({ message: "Usuario creado correctamente" });
  } catch (e) { console.error(e); res.status(500).json({ error: "Error al crear usuario" }); }
});

router.get("/usuarios", authMiddleware, async (req, res) => {
  const { rol } = req.user;
  if (rol !== "admin") return res.status(403).json({ error: "No autorizado" });
  const db = await dbPromise;
  const usuarios = await db.all("SELECT id, usuario, rol FROM usuarios");
  res.json(usuarios);
});

router.delete("/usuarios/:id", authMiddleware, async (req, res) => {
  const { rol } = req.user;
  if (rol !== "admin") return res.status(403).json({ error: "No autorizado" });
  const db = await dbPromise;
  const user = await db.get("SELECT * FROM usuarios WHERE id = ?", req.params.id);
  if (user?.rol === "admin") return res.status(400).json({ error: "No puedes eliminar un administrador" });
  await db.run("DELETE FROM usuarios WHERE id = ?", req.params.id);
  res.json({ message: "Usuario eliminado correctamente" });
});

export default router;
