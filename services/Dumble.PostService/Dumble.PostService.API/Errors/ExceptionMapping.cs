using System.Net;
using FluentValidation;
using Microsoft.AspNetCore.Diagnostics;
using Microsoft.AspNetCore.Mvc;

namespace Dumble.PostService.API.Errors;

internal static class ExceptionMapping
{
    public static IApplicationBuilder UseExceptionMapping(this IApplicationBuilder app)
    {
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

                var problem = new ProblemDetails
                {
                    Status = (int)status,
                    Title = title,
                    Detail = status == HttpStatusCode.InternalServerError ? null : ex?.Message
                };

                await ctx.Response.WriteAsJsonAsync(problem);
            });
        });
    }
}
