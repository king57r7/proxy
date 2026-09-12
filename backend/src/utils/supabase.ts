import { createClient, SupabaseClient } from '@supabase/supabase-js';
import config from '@/config';
import { Logger } from '@/utils/logger';

const logger = new Logger('Supabase');

// Public client (for user operations)
export const supabaseClient: SupabaseClient = createClient(
  config.supabase.url,
  config.supabase.anonKey,
  {
    auth: {
      autoRefreshToken: true,
      persistSession: false
    }
  }
);

// Service role client (for admin operations)
export const supabaseAdmin: SupabaseClient = createClient(
  config.supabase.url,
  config.supabase.serviceRoleKey,
  {
    auth: {
      autoRefreshToken: false,
      persistSession: false
    }
  }
);

// Test connection
export async function testSupabaseConnection(): Promise<boolean> {
  try {
    const { data, error } = await supabaseAdmin
      .from('users')
      .select('id')
      .limit(1);

    if (error) {
      logger.error('Supabase connection test failed', error);
      return false;
    }

    logger.info('Supabase connection successful');
    return true;
  } catch (error) {
    logger.error('Supabase connection test error', error as Error);
    return false;
  }
}

// Helper: Execute RPC function
export async function callRPC(
  functionName: string,
  params?: Record<string, any>,
  isAdmin: boolean = false
): Promise<any> {
  try {
    const client = isAdmin ? supabaseAdmin : supabaseClient;
    const { data, error } = await client.rpc(functionName, params || {});

    if (error) {
      logger.error(`RPC call failed: ${functionName}`, error);
      throw error;
    }

    return data;
  } catch (error) {
    logger.error(`RPC execution error: ${functionName}`, error as Error);
    throw error;
  }
}

// Helper: Insert with error handling
export async function insert<T>(
  table: string,
  data: T,
  isAdmin: boolean = false
): Promise<{ data: T[]; error: any }> {
  const client = isAdmin ? supabaseAdmin : supabaseClient;
  return (client.from(table) as any).insert([data]).select();
}

// Helper: Update with error handling
export async function update<T>(
  table: string,
  data: Partial<T>,
  match: Record<string, any>,
  isAdmin: boolean = false
): Promise<{ data: T[]; error: any }> {
  const client = isAdmin ? supabaseAdmin : supabaseClient;
  let query = client.from(table).update(data as any);

  // Apply all match conditions
  Object.entries(match).forEach(([key, value]) => {
    query = query.eq(key, value);
  });

  return (query as any).select();
}

// Helper: Fetch with filters
export async function fetch<T>(
  table: string,
  filters?: Record<string, any>,
  options?: { limit?: number; offset?: number; orderBy?: string; isAdmin?: boolean }
): Promise<{ data: T[]; error: any }> {
  const client = options?.isAdmin ? supabaseAdmin : supabaseClient;
  let query = client.from(table).select('*');

  // Apply filters
  if (filters) {
    Object.entries(filters).forEach(([key, value]) => {
      if (Array.isArray(value)) {
        query = query.in(key, value);
      } else if (value !== null && value !== undefined) {
        query = query.eq(key, value);
      }
    });
  }

  // Apply ordering
  if (options?.orderBy) {
    const [column, direction] = options.orderBy.split(':');
    query = query.order(column, { ascending: direction !== 'desc' });
  }

  // Apply pagination
  if (options?.limit) {
    query = query.limit(options.limit);
  }
  if (options?.offset) {
    query = query.range(options.offset, options.offset + (options.limit || 10) - 1);
  }

  return query as any;
}

// Helper: Get single record
export async function fetchOne<T>(
  table: string,
  match: Record<string, any>,
  isAdmin: boolean = false
): Promise<T | null> {
  const client = isAdmin ? supabaseAdmin : supabaseClient;
  let query = client.from(table).select('*');

  Object.entries(match).forEach(([key, value]) => {
    query = query.eq(key, value);
  });

  const { data, error } = await query.limit(1).single();

  if (error && error.code !== 'PGRST116') {
    // PGRST116 = no rows found
    logger.error(`Fetch one error: ${table}`, error);
  }

  return data as T | null;
}

// Helper: Delete
export async function remove(
  table: string,
  match: Record<string, any>,
  isAdmin: boolean = false
): Promise<void> {
  const client = isAdmin ? supabaseAdmin : supabaseClient;
  let query = client.from(table).delete();

  Object.entries(match).forEach(([key, value]) => {
    query = query.eq(key, value);
  });

  const { error } = await query;

  if (error) {
    logger.error(`Delete error: ${table}`, error);
    throw error;
  }
}

// Helper: Count records
export async function count(
  table: string,
  filters?: Record<string, any>
): Promise<number> {
  let query = supabaseClient
    .from(table)
    .select('*', { count: 'exact', head: true });

  if (filters) {
    Object.entries(filters).forEach(([key, value]) => {
      query = query.eq(key, value);
    });
  }

  const { count: total } = await query;
  return total || 0;
}

// Helper: Transaction-like operation (batch inserts)
export async function batchInsert<T>(
  table: string,
  data: T[],
  batchSize: number = 1000,
  isAdmin: boolean = false
): Promise<{ data: T[]; error: any }> {
  const client = isAdmin ? supabaseAdmin : supabaseClient;
  const batches = [];

  for (let i = 0; i < data.length; i += batchSize) {
    const batch = data.slice(i, i + batchSize);
    batches.push((client.from(table) as any).insert(batch).select());
  }

  const results = await Promise.all(batches);

  // Combine results
  const allData: T[] = [];
  let errorOccurred = null;

  results.forEach(result => {
    if (result.error) {
      errorOccurred = result.error;
    } else if (result.data) {
      allData.push(...result.data);
    }
  });

  return { data: allData, error: errorOccurred };
}

// Helper: Get authentication headers
export function getAuthHeaders(token?: string): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'apikey': config.supabase.serviceRoleKey
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return headers;
}

export default {
  supabaseClient,
  supabaseAdmin,
  testSupabaseConnection,
  callRPC,
  insert,
  update,
  fetch,
  fetchOne,
  remove,
  count,
  batchInsert,
  getAuthHeaders
};
