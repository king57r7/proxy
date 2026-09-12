import crypto from 'crypto';
import config from '@/config';
import { Logger } from '@/utils/logger';

const logger = new Logger('Encryption');

class EncryptionService {
  private algorithm: string;
  private key: Buffer;
  private iv: string = 'initialization_vector_16bytes!'; // 16 bytes

  constructor() {
    this.algorithm = config.encryption.algorithm;
    
    // Ensure key is 32 bytes for aes-256
    const keyString = config.encryption.key;
    if (keyString.length < 32) {
      this.key = Buffer.alloc(32);
      Buffer.from(keyString).copy(this.key);
      logger.warn('Encryption key is shorter than 32 bytes, padding with zeros');
    } else {
      this.key = Buffer.from(keyString.substring(0, 32));
    }
  }

  /**
   * Encrypt sensitive data
   */
  encrypt(text: string): string {
    try {
      const cipher = crypto.createCipheriv(
        this.algorithm,
        this.key,
        Buffer.from(this.iv)
      );

      let encrypted = cipher.update(text, 'utf8', 'hex');
      encrypted += cipher.final('hex');

      return encrypted;
    } catch (error) {
      logger.error('Encryption failed', error as Error);
      throw new Error('Failed to encrypt data');
    }
  }

  /**
   * Decrypt sensitive data
   */
  decrypt(encryptedText: string): string {
    try {
      const decipher = crypto.createDecipheriv(
        this.algorithm,
        this.key,
        Buffer.from(this.iv)
      );

      let decrypted = decipher.update(encryptedText, 'hex', 'utf8');
      decrypted += decipher.final('utf8');

      return decrypted;
    } catch (error) {
      logger.error('Decryption failed', error as Error);
      throw new Error('Failed to decrypt data');
    }
  }

  /**
   * Hash a password (for verification purposes)
   */
  hashPassword(password: string, salt: string = ''): string {
    try {
      const hash = crypto
        .pbkdf2Sync(password, salt || this.iv, 1000, 64, 'sha512')
        .toString('hex');
      return hash;
    } catch (error) {
      logger.error('Password hashing failed', error as Error);
      throw new Error('Failed to hash password');
    }
  }

  /**
   * Generate a random string (for tokens, codes, etc.)
   */
  generateRandomString(length: number = 32): string {
    return crypto.randomBytes(length).toString('hex');
  }

  /**
   * Generate a UUID
   */
  generateUUID(): string {
    return crypto.randomUUID();
  }

  /**
   * Create HMAC for signing
   */
  createHMAC(data: string, secret: string = this.key.toString('hex')): string {
    return crypto
      .createHmac('sha256', secret)
      .update(data)
      .digest('hex');
  }

  /**
   * Verify HMAC signature
   */
  verifyHMAC(data: string, signature: string, secret: string = this.key.toString('hex')): boolean {
    const calculated = this.createHMAC(data, secret);
    return crypto.timingSafeEqual(
      Buffer.from(calculated),
      Buffer.from(signature)
    );
  }
}

export const encryptionService = new EncryptionService();

export default encryptionService;
