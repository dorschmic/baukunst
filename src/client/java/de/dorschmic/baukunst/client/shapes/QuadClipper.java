package de.dorschmic.baukunst.client.shapes;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Cuts a quad of a full-cube model down to a box (e.g. the lower half for a slab),
 * moving faces onto the box surface and cropping the texture coordinates to match –
 * the same result as a hand-made slab/stair model.
 */
public final class QuadClipper {
	private static final float EPS = 1.0E-4F;

	private QuadClipper() {
	}

	/**
	 * @return false if the quad should be dropped
	 */
	public static boolean clip(MutableQuadView quad, AABB box) {
		Vector3f p0 = quad.copyPos(0, null);
		Vector3f e1 = quad.copyPos(1, null).sub(p0);
		Vector3f e2 = quad.copyPos(3, null).sub(p0);
		float l1 = e1.lengthSquared();
		float l2 = e2.lengthSquared();
		if (l1 < EPS || l2 < EPS) {
			return false;
		}
		Vector2f uv0 = quad.copyUv(0, null);
		Vector2f du1 = quad.copyUv(1, null).sub(uv0);
		Vector2f du2 = quad.copyUv(3, null).sub(uv0);

		Vector3f[] pos = new Vector3f[4];
		Vector2f[] uv = new Vector2f[4];
		for (int i = 0; i < 4; i++) {
			Vector3f p = quad.copyPos(i, null);
			p.set(clamp(p.x, box.minX, box.maxX), clamp(p.y, box.minY, box.maxY), clamp(p.z, box.minZ, box.maxZ));
			Vector3f rel = new Vector3f(p).sub(p0);
			float s = rel.dot(e1) / l1;
			float t = rel.dot(e2) / l2;
			pos[i] = p;
			uv[i] = new Vector2f(uv0).add(new Vector2f(du1).mul(s)).add(new Vector2f(du2).mul(t));
		}
		// Drop quads that collapsed to a line.
		Vector3f a = new Vector3f(pos[1]).sub(pos[0]);
		Vector3f b = new Vector3f(pos[3]).sub(pos[0]);
		if (a.cross(b).lengthSquared() < EPS * EPS) {
			return false;
		}
		for (int i = 0; i < 4; i++) {
			quad.pos(i, pos[i]);
			quad.uv(i, uv[i]);
		}

		Direction cull = quad.cullFace();
		if (cull != null && !onBlockSide(pos, cull)) {
			quad.cullFace(null);
		}
		return true;
	}

	private static boolean onBlockSide(Vector3f[] pos, Direction side) {
		float target = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0F : 0.0F;
		for (Vector3f p : pos) {
			float v = switch (side.getAxis()) {
				case X -> p.x;
				case Y -> p.y;
				case Z -> p.z;
			};
			if (Math.abs(v - target) > EPS) {
				return false;
			}
		}
		return true;
	}

	private static float clamp(float v, double min, double max) {
		return (float) Math.max(min, Math.min(max, v));
	}
}
