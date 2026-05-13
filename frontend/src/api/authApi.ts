import client from './client.ts'
import { LoginResponse } from '../types'

export const authApi = {
  login: (username: string, password: string) =>
    client.post<LoginResponse>('/auth/login', { username, password }),
  verify: () => client.get('/auth/verify'),
  me: () => client.get('/auth/me'),
}
