import { userManager } from './oidcConfig'
import { createAuthSession } from './session'

/** The app-wide customer session (single UserManager, single-flight renewal). */
export const authSession = createAuthSession(userManager)
