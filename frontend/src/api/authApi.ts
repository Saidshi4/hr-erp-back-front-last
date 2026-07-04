import client from './client.ts'
import { LoginResponse } from '../types'

export const authApi = {
  login: (email: string, password: string) =>
    client.post<LoginResponse>('/auth/login', { email, password }),
  signup: (data: {
    email: string
    firstName: string
    lastName: string
    password: string
    role?: string
  }) => client.post<LoginResponse>('/auth/signup', data),
  verify: () => client.get('/auth/verify'),
  me: () => client.get('/auth/me'),
}
