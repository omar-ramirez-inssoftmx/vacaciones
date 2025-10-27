import express from "express";
import { dbPromise } from "../db.js";
import { authMiddleware } from "../middleware/authMiddleware.js";

const router = express.Router();
router.use(authMiddleware);

router.get("/", async (req, res) => {
  const db = await dbPromise;
  const { rol, id } = req.user;
  const rows = rol === "admin"
    ? await db.all("SELECT * FROM solicitudes")
    : await db.all("SELECT * FROM solicitudes WHERE usuario_id = ?", id);
  res.json(rows);
});

router.post("/", async (req, res) => {
  const db = await dbPromise;
  const { fecha_inicio, fecha_fin, motivo } = req.body;
  const { id } = req.user;
  await db.run("INSERT INTO solicitudes (usuario_id, fecha_inicio, fecha_fin, motivo) VALUES (?, ?, ?, ?)",
    id, fecha_inicio, fecha_fin, motivo);
  res.json({ message: "Solicitud registrada" });
});

router.put("/:id", async (req, res) => {
  const { rol } = req.user;
  if (rol !== "admin") return res.status(403).json({ error: "No autorizado" });
  const { estado } = req.body;
  const db = await dbPromise;
  await db.run("UPDATE solicitudes SET estado = ? WHERE id = ?", estado, req.params.id);
  res.json({ message: "Estado actualizado correctamente" });
});

export default router;
