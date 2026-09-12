import { Router } from 'express';
import { supabaseAdmin, supabaseClient } from '../utils/supabase';
const router = Router();
router.post('/register', async (req, res) => {
  const { email, password, fullName } = req.body ?? {};
  if (typeof email !== 'string' || !/^\S+@\S+\.\S+$/.test(email) || typeof password !== 'string' || password.length < 8) return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Valid email and password of at least 8 characters are required' } });
  const created = await supabaseAdmin.auth.admin.createUser({ email, password, email_confirm: false, user_metadata: { full_name: fullName ?? '' } });
  if (created.error || !created.data.user) return res.status(400).json({ error: { code: 'REGISTRATION_FAILED', message: created.error?.message ?? 'Could not create user' } });
  const profile = await supabaseAdmin.from('users').insert({ auth_id: created.data.user.id, email, full_name: fullName ?? null }).select('id,email,full_name,role').single();
  if (profile.error) { await supabaseAdmin.auth.admin.deleteUser(created.data.user.id); return res.status(500).json({ error: { code: 'PROFILE_CREATE_FAILED', message: 'Could not create user profile' } }); }
  return res.status(201).json({ data: profile.data });
});
router.post('/login', async (req, res) => {
  const { email, password } = req.body ?? {};
  const result = await supabaseClient.auth.signInWithPassword({ email, password });
  if (result.error || !result.data.session) return res.status(401).json({ error: { code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect' } });
  return res.json({ data: { accessToken: result.data.session.access_token, refreshToken: result.data.session.refresh_token, expiresAt: result.data.session.expires_at } });
});
router.post('/refresh', async (req, res) => { const result = await supabaseClient.auth.refreshSession({ refresh_token: req.body?.refreshToken }); if (result.error || !result.data.session) return res.status(401).json({ error: { code: 'REFRESH_FAILED', message: 'Refresh token is invalid' } }); return res.json({ data: { accessToken: result.data.session.access_token, refreshToken: result.data.session.refresh_token, expiresAt: result.data.session.expires_at } }); });
export default router;
