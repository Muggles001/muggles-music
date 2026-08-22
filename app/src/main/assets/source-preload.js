'use strict'

globalThis.__muggles_setup = (scriptInfoJson) => {
  delete globalThis.__muggles_setup
  const nativeHttp = globalThis.__muggles_http__
  const nativeEmit = globalThis.__muggles_emit__
  const nativeSetTimeout = globalThis.__muggles_set_timeout__
  const nativeMd5 = globalThis.__muggles_md5__
  const nativeAes = globalThis.__muggles_aes__
  const nativeRsa = globalThis.__muggles_rsa__
  const nativeRandom = globalThis.__muggles_random__
  const nativeHexToBase64 = globalThis.__muggles_hex_to_base64__
  const nativeBase64ToHex = globalThis.__muggles_base64_to_hex__
  const scriptInfo = JSON.parse(scriptInfoJson)
  const callbacks = new Map()
  let timeoutId = 1
  let requestHandler = null

  const utf8Encode = (value) => {
    const text = unescape(encodeURIComponent(String(value)))
    const bytes = new Uint8Array(text.length)
    for (let i = 0; i < text.length; i++) bytes[i] = text.charCodeAt(i)
    return bytes
  }
  const utf8Decode = (bytes) => {
    let text = ''
    for (const value of bytes) text += String.fromCharCode(value & 0xff)
    return decodeURIComponent(escape(text))
  }
  const hexEncode = (bytes) => Array.from(bytes)
    .map(value => (value & 0xff).toString(16).padStart(2, '0')).join('')
  const hexDecode = (value) => {
    const text = String(value).replace(/\s/g, '')
    if (text.length % 2) throw new Error('Invalid hex input')
    const bytes = new Uint8Array(text.length / 2)
    for (let i = 0; i < bytes.length; i++) bytes[i] = parseInt(text.slice(i * 2, i * 2 + 2), 16)
    return bytes
  }
  const base64Encode = bytes => nativeHexToBase64(hexEncode(bytes))
  const base64Decode = value => hexDecode(nativeBase64ToHex(String(value)))
  const toBytes = (value, format = 'utf8') => {
    if (value instanceof Uint8Array) return new Uint8Array(value)
    if (Array.isArray(value)) return new Uint8Array(value)
    if (value && typeof value === 'object') return new Uint8Array(Object.values(value))
    if (format === 'base64') return base64Decode(value)
    if (format === 'hex') return hexDecode(value)
    return utf8Encode(value)
  }
  const bytesToString = (value, format = 'utf8') => {
    const bytes = toBytes(value)
    if (format === 'base64') return base64Encode(bytes)
    if (format === 'hex') return hexEncode(bytes)
    return utf8Decode(bytes)
  }

  const EVENT_NAMES = {
    request: 'request',
    inited: 'inited',
    updateAlert: 'updateAlert',
  }
  const supportedSources = new Set(['kw', 'kg', 'tx', 'wy', 'mg', 'local'])
  const supportedQuality = new Set(['128k', '320k', 'flac', 'flac24bit'])
  const supportedActions = {
    kw: new Set(['musicUrl']),
    kg: new Set(['musicUrl']),
    tx: new Set(['musicUrl']),
    wy: new Set(['musicUrl']),
    mg: new Set(['musicUrl']),
    local: new Set(['musicUrl', 'lyric', 'pic']),
  }

  const normalizeSources = (sources) => {
    if (!sources || typeof sources !== 'object') throw new Error('Invalid source capabilities')
    const result = {}
    for (const [key, source] of Object.entries(sources)) {
      if (!supportedSources.has(key) || !source || source.type !== 'music') continue
      const actions = Array.isArray(source.actions)
        ? source.actions.filter(action => supportedActions[key].has(action)) : []
      const qualitys = key === 'local' ? [] : (Array.isArray(source.qualitys)
        ? source.qualitys.filter(quality => supportedQuality.has(quality)) : [])
      if (!actions.length) continue
      result[key] = {
        name: typeof source.name === 'string' ? source.name : key,
        type: 'music',
        actions,
        qualitys,
      }
    }
    if (!Object.keys(result).length) throw new Error('The script did not register a supported source')
    return result
  }

  const request = (url, options, callback) => {
    if (typeof callback !== 'function') throw new Error('Request callback is required')
    try {
      const raw = nativeHttp(String(url), JSON.stringify(options || {}))
      const result = JSON.parse(raw)
      if (result.error) {
        callback(new Error(result.error), null, null)
      } else {
        callback(null, result.response, result.response.body)
      }
    } catch (error) {
      callback(error, null, null)
    }
    return () => {}
  }

  const lx = {
    version: '2.0.0',
    env: 'mobile',
    currentScriptInfo: scriptInfo,
    EVENT_NAMES,
    request,
    on(eventName, handler) {
      if (eventName !== EVENT_NAMES.request || typeof handler !== 'function') {
        throw new Error('Unsupported event handler')
      }
      requestHandler = handler
    },
    send(eventName, data) {
      if (eventName === EVENT_NAMES.inited) {
        nativeEmit('inited', JSON.stringify({ sources: normalizeSources(data && data.sources) }))
        return Promise.resolve()
      }
      if (eventName === EVENT_NAMES.updateAlert) {
        nativeEmit('updateAlert', JSON.stringify(data || {}))
        return Promise.resolve()
      }
      return Promise.reject(new Error('Unsupported event'))
    },
    utils: {
      buffer: {
        from: toBytes,
        bufToString: bytesToString,
      },
      crypto: {
        md5: value => nativeMd5(String(value)),
        randomBytes: size => base64Decode(nativeRandom(Number(size))),
        aesEncrypt: (buffer, mode, key, iv) => base64Decode(nativeAes(
          base64Encode(toBytes(buffer)), String(mode), base64Encode(toBytes(key)),
          iv == null ? '' : base64Encode(toBytes(iv)))),
        rsaEncrypt: (buffer, key) => base64Decode(nativeRsa(
          base64Encode(toBytes(buffer)), String(key))),
      },
      zlib: {},
    },
  }

  globalThis.setTimeout = (callback, delay = 0, ...args) => {
    if (typeof callback !== 'function') throw new Error('Timeout callback is required')
    const id = timeoutId++
    callbacks.set(id, () => callback(...args))
    nativeSetTimeout(id, Math.max(0, Math.min(Number(delay) || 0, 60000)))
    return id
  }
  globalThis.clearTimeout = id => callbacks.delete(id)
  globalThis.__muggles_fire_timeout = id => {
    const callback = callbacks.get(id)
    if (!callback) return
    callbacks.delete(id)
    callback()
  }
  globalThis.__muggles_call = (requestId, source, action, infoJson) => {
    if (typeof requestHandler !== 'function') {
      nativeEmit('result', JSON.stringify({ requestId, ok: false, error: 'Source is not initialized' }))
      return
    }
    let info
    try {
      info = JSON.parse(infoJson)
    } catch (error) {
      nativeEmit('result', JSON.stringify({ requestId, ok: false, error: error.message }))
      return
    }
    Promise.resolve(requestHandler({ source, action, info }))
      .then(data => nativeEmit('result', JSON.stringify({ requestId, ok: true, data })))
      .catch(error => nativeEmit('result', JSON.stringify({
        requestId,
        ok: false,
        error: error && error.message ? error.message : String(error),
      })))
  }

  Object.freeze(lx.EVENT_NAMES)
  Object.freeze(lx.utils)
  Object.freeze(lx)
  globalThis.lx = lx
  globalThis.eval = () => { throw new Error('eval is not available') }
  globalThis.Function = () => { throw new Error('Function is not available') }
}
