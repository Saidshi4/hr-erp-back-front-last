import client from './client.ts'
import { LoginResponse } from '../types'

export const authApi = {
  login: (username: string, password: string) =>
    client.post<LoginResponse>('/auth/login', { username, password }),
  signup: (data: {
    username: string
    email?: string
    firstName: string
    lastName: string
    password: string
    role?: string
  }) => client.post<LoginResponse>('/auth/signup', data),
  verify: () => client.get('/auth/verify'),
  me: () => client.get('/auth/me'),
}
