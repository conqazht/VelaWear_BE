## Vị trí trong luồng request

```
HTTP Request
    │
    ▼
┌──────────────────────────────────────────┐
│  Servlet Filter Chain                    │  ← Tầng Servlet (javax/jakarta)
│                                          │
│  ┌─ CorsFilter                           │
│  ├─ CsrfFilter                           │
│  ├─ BearerTokenAuthenticationFilter      │  ← JWT decode + tạo Authentication
│  ├─ AuthorizationFilter                  │  ← hasRole(), hasAuthority()
│  ├─ [Custom Filter — nếu thêm ở đây]     │
│  └─ ...                                  │
└──────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────┐
│  DispatcherServlet                       │  ← Tầng Spring MVC
│                                          │
│  ┌─ HandlerInterceptor.preHandle()       │  ← [Interceptor — ở đây]
│  ├─ Controller method                    │
│  ├─ HandlerInterceptor.postHandle()      │
│  └─ HandlerInterceptor.afterCompletion() │
└──────────────────────────────────────────┘
```

Filter chạy **trước** DispatcherServlet, Interceptor chạy **sau** DispatcherServlet nhưng **trước** Controller.

## So sánh trong bối cảnh permission-based RBAC

### Filter (Security Filter)

Filter thuộc tầng Servlet, chạy rất sớm. Spring Security bản thân nó là một chuỗi Filter. Nếu bạn viết custom Filter để check permission, nó sẽ nằm cùng tầng với `BearerTokenAuthenticationFilter` và `AuthorizationFilter`.

Ưu điểm: chặn request sớm nhất, trước khi Spring MVC xử lý bất cứ thứ gì. Request bị reject sẽ không tốn resource đi qua DispatcherServlet, handler mapping, argument resolver... Và quan trọng nhất, nó **tích hợp tự nhiên** với Spring Security — vì Spring Security chính là Filter.

Nhược điểm: ở tầng Filter, bạn **không có thông tin về controller method**. Bạn chỉ có raw `HttpServletRequest` — tức là chỉ biết request path (`/api/v1/users/5`) và HTTP method (`GET`). Không biết handler nào sẽ xử lý, không có annotation metadata.

### Interceptor (HandlerInterceptor)

Interceptor thuộc tầng Spring MVC, chạy sau khi DispatcherServlet đã xác định được controller method nào sẽ handle request.

Ưu điểm: trong `preHandle()` bạn có thể truy cập `HandlerMethod`, tức là biết chính xác method nào trong controller nào sẽ xử lý. Từ đó đọc được annotation trên method (ví dụ `@PreAuthorize`, hoặc custom annotation như `@RequirePermission("CREATE_USER")`).

Nhược điểm: request đã đi qua toàn bộ Security Filter Chain rồi. Nếu reject ở đây thì đã tốn công decode JWT, đã tốn DispatcherServlet resolve handler. Và nó **tách rời** khỏi Spring Security — exception handling, 401/403 response format phải tự xử lý, không được Spring Security lo.

## Với dự án của bạn: nên dùng cái nào?

Dự án có Permission entity map **(apiPath + httpMethod) → Role**. Logic check quyền là:

```
Lấy request path + method (permission)
  → Query DB: tìm Permissions theo role của user
  → So sánh permission truy cập (và permissions của user)
  → Có → cho qua / Không → 403
```

Logic này **chỉ cần request path + HTTP method**, không cần biết handler method hay annotation. Vì vậy **Filter phù hợp hơn**.

Cụ thể hơn, bạn không cần viết raw Servlet Filter. Spring Security cung cấp cơ chế **custom `AuthorizationManager`** — nó hook vào `AuthorizationFilter` (filter cuối cùng trong Security Filter Chain), nơi mà `Authentication` đã có sẵn trong `SecurityContext`:

```
BearerTokenAuthenticationFilter  → JWT đã decode, Authentication đã có
        ↓
AuthorizationFilter              → Gọi AuthorizationManager.check()
        ↓                           Tại đây bạn custom logic:
                                     request path + method → query Permission
                                     → so sánh với roles của user
                                     → cho phép hoặc deny
```

Đây là cách "đúng Spring Security" — không viết custom Filter riêng, không dùng Interceptor, mà tận dụng đúng extension point mà framework cung cấp.

## Tóm lại 3 lựa chọn

**Custom Servlet Filter** — hoạt động được, nhưng phải tự quản lý thứ tự filter, tự xử lý exception. Không cần thiết vì Spring Security đã có extension point tốt hơn.

**HandlerInterceptor** — không phù hợp vì: chạy muộn (sau DispatcherServlet), tách rời khỏi Spring Security, phải tự format 403 response. Interceptor phù hợp hơn cho cross-cutting concerns không liên quan security (logging, rate limiting, audit...).

**Custom AuthorizationManager trong SecurityFilterChain** — phù hợp nhất. Đúng tầng (security), đúng thời điểm (sau authentication, trước controller), tích hợp tự nhiên với Spring Security (exception handling, 401/403 tự động), và đúng với kiến trúc dự án đã thiết kế ("no custom filter").
