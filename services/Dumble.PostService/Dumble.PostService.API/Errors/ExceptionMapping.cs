using System.Net;
using FluentValidation;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.AspNetCore.Mvc;

namespace Dumble.PostService.API.Errors;

internal static class ExceptionMapping
{
    public static IApplicationBuilder UseExceptionMapping(this IApplicationBuilder app)
    {
        var env = app.ApplicationServices.GetRequiredService<IHostEnvironment>();
        return app.UseExceptionHandler(handler =>
        {
            handler.Run(async ctx =>
            {
                var feature = ctx.Features.Get<IExceptionHandlerFeature>();
                var ex = feature?.Error;

                var (status, title) = ex switch
                {
                    KeyNotFoundException => (HttpStatusCode.NotFound, "Resource not found"),
                    UnauthorizedAccessException => (HttpStatusCode.Forbidden, "Forbidden"),
                    ValidationException => (HttpStatusCode.BadRequest, "Validation failed"),
                    ArgumentException => (HttpStatusCode.BadRequest, "Invalid request"),
                    _ => (HttpStatusCode.InternalServerError, "Unexpected error")
                };

                ctx.Response.StatusCode = (int)status;
                ctx.Response.ContentType = "application/problem+json";

                // Production: hide internals on 500 — no stack traces over the wire.
                // Development: expose the exception so local debugging doesn't
                // require attaching a debugger to see what blew up.
                string? detail;
                if (status == HttpStatusCode.InternalServerError)
                {
                    detail = env.IsDevelopment() ? ex?.ToString() : null;
                }
                else
                {
                    detail = ex?.Message;
                }

                var problem = new ProblemDetails
                {
                    Status = (int)status,
                    Title = title,
                    Detail = detail
                };

                await ctx.Response.WriteAsJsonAsync(problem);
            });
        });
    }
}
