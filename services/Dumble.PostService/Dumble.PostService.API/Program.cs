using System.Net;
using Dumble.PostService.API.Authentication;
using Dumble.PostService.API.Errors;
using Dumble.PostService.Application;
using Dumble.PostService.Infrastructure;
using Dumble.PostService.Infrastructure.Persistence;
using Microsoft.EntityFrameworkCore;
using FastEndpoints;
using FastEndpoints.Swagger;
using FluentValidation;
using Microsoft.AspNetCore.Authentication;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

builder.WebHost.ConfigureKestrel(opt =>
{
    opt.AddServerHeader = false;
    // Cap request body at the form-options limit. Without this Kestrel's
    // default (~30 MB) leaves room for a multipart payload with several
    // files near the cap to fan out into multiple in-memory streams against
    // Cloudinary per request.
    opt.Limits.MaxRequestBodySize = 25L * 1024 * 1024;
});

// Multipart form upload limits — per-request 25 MB, per non-file field 4 KB.
// Per-file enforcement lives in CreatePostCommandHandler (rejects > 5 MB
// before opening the file stream).
builder.Services.Configure<FormOptions>(opt =>
{
    opt.MultipartBodyLengthLimit = 25L * 1024 * 1024;
    opt.ValueLengthLimit = 4 * 1024;
});

builder.Services.AddApplication();
builder.Services.AddInfrastructure(builder.Configuration);

builder.Services.AddFastEndpoints();
builder.Services.AddValidatorsFromAssemblyContaining<Program>();

if (builder.Environment.IsDevelopment())
{
    builder.Services.SwaggerDocument(o =>
    {
        o.DocumentSettings = s =>
        {
            s.Title = "Dumble Post Service API";
            s.Version = "v1";
        };
    });
}

var jwtSecret = builder.Configuration["Jwt:Secret"]
    ?? builder.Configuration["JWT_SECRET"]
    ?? throw new InvalidOperationException("JWT_SECRET env var is required");
var signingKey = new SymmetricSecurityKey(Convert.FromBase64String(jwtSecret));

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.RequireHttpsMetadata = builder.Environment.IsProduction();
        // Keep claim names as-issued (sub, userId, displayName, etc.) so the
        // .NET-side claim-name remap doesn't fight the Java auth's wire format.
        options.MapInboundClaims = false;
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = false,
            ValidateAudience = false,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            RequireSignedTokens = true,
            IssuerSigningKey = signingKey,
            ClockSkew = TimeSpan.FromSeconds(30)
        };
    });

builder.Services.AddAuthorization();
builder.Services.AddTransient<IClaimsTransformation, RolesClaimsTransformation>();

// ForwardedHeaders only honours X-Forwarded-* when the immediate peer is on
// the trusted-proxy list. Without this guard, any client can spoof the headers
// and downstream IP-based controls silently break. Configure via
// Gateway:TrustedProxies (CSV of IPs). Empty / unset → keep the ASP.NET
// loopback default rather than clearing both allowlists.
builder.Services.Configure<ForwardedHeadersOptions>(opt =>
{
    opt.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    var trustedProxiesCsv = builder.Configuration["Gateway:TrustedProxies"];
    if (!string.IsNullOrWhiteSpace(trustedProxiesCsv))
    {
        opt.KnownProxies.Clear();
        opt.KnownNetworks.Clear();
        foreach (var raw in trustedProxiesCsv.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            if (IPAddress.TryParse(raw, out var ip))
                opt.KnownProxies.Add(ip);
        }
    }
});

// MassTransit (v8) auto-registers a "masstransit-bus" health check that
// reflects real broker connectivity, so we don't hand-roll a RabbitMQ check —
// the previous custom one inspected ProbeResult.Status, which doesn't exist on
// MassTransit's ProbeResult and never compiled. Just add the DB check; the
// bus check is contributed by AddMassTransit and shows up on /health/ready.
builder.Services.AddHealthChecks()
    .AddDbContextCheck<PostDbContext>(name: "database");

var app = builder.Build();

// Provision the schema on startup. The service owns its database and ships no separate
// migration step, so create the model's tables if they aren't there yet. Fails fast if the
// database is unreachable, which is the intended behaviour behind depends_on.
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<PostDbContext>();
    db.Database.EnsureCreated();

    // EnsureCreated() only provisions schema when the database does NOT already
    // exist; it will not add tables introduced after the first run. The
    // reactions / comment_reactions tables were added later, so on existing
    // deployments they are missing and every reaction call 500s. Create them
    // idempotently here — a no-op on fresh databases (EnsureCreated already made
    // them) and on subsequent restarts. Column/table names match the EF model so
    // the context reads and writes them without a mismatch.
    db.Database.ExecuteSqlRaw("""
        CREATE TABLE IF NOT EXISTS reactions (
            "Id" uuid NOT NULL,
            "PostId" uuid NOT NULL,
            "UserId" character varying(100) NOT NULL,
            "DisplayName" character varying(200) NOT NULL,
            "ProfileImage" character varying(500) NULL,
            "Type" character varying(50) NOT NULL,
            "CreatedAt" timestamp with time zone NOT NULL,
            CONSTRAINT "PK_reactions" PRIMARY KEY ("Id"),
            CONSTRAINT "FK_reactions_posts_PostId" FOREIGN KEY ("PostId")
                REFERENCES posts ("Id") ON DELETE CASCADE
        );
        CREATE UNIQUE INDEX IF NOT EXISTS ix_reactions_post_id_user_id
            ON reactions ("PostId", "UserId");

        CREATE TABLE IF NOT EXISTS comment_reactions (
            "Id" uuid NOT NULL,
            "CommentId" uuid NOT NULL,
            "UserId" character varying(100) NOT NULL,
            "DisplayName" character varying(200) NOT NULL,
            "ProfileImage" character varying(500) NULL,
            "Type" character varying(50) NOT NULL,
            "CreatedAt" timestamp with time zone NOT NULL,
            CONSTRAINT "PK_comment_reactions" PRIMARY KEY ("Id"),
            CONSTRAINT "FK_comment_reactions_comments_CommentId" FOREIGN KEY ("CommentId")
                REFERENCES comments ("Id") ON DELETE CASCADE
        );
        CREATE UNIQUE INDEX IF NOT EXISTS ix_comment_reactions_comment_id_user_id
            ON comment_reactions ("CommentId", "UserId");
        """);
}

app.UseForwardedHeaders();
app.UseExceptionMapping();

app.UseAuthentication();
app.UseAuthorization();

app.MapHealthChecks("/health/live");
app.MapHealthChecks("/health/ready");

app.UseFastEndpoints(c => c.Errors.UseProblemDetails());

if (app.Environment.IsDevelopment())
{
    app.UseSwaggerGen();
}

app.Run();

public partial class Program;
