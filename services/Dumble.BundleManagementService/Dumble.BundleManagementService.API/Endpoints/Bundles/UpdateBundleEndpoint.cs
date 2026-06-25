using System.Net;
using Dumble.BundleManagementService.Application.Features.Bundles.Commands.UpdateBundle;
using Dumble.BundleManagementService.Contracts.Bundles.UpdateBundle;
using FastEndpoints;
using MediatR;
using Void = FastEndpoints.Void;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class UpdateBundleEndpoint(ISender mediator) : Endpoint<UpdateBundleRequest>
{
    public override void Configure()
    {
        Put("/api/bundles/{id}");
        Roles("ADMIN", "GYM_OWNER", "GYM", "TRAINER");
        Options(x => x.WithTags("Bundles"));
    }

    public override async Task<object?> ExecuteAsync(UpdateBundleRequest req, CancellationToken ct)
    {
        try
        {
            await mediator.Send(new UpdateBundleCommand(
                req.Id,
                req.Name,
                req.Description,
                req.Price,
                req.Status,
                req.ExpiresOn,
                req.CategoryId), ct);
        }
        catch (Exception ex)
        {
            // Temporary diagnostic: surface the real failure instead of letting it
            // bubble up as a masked gateway 500 ("An unexpected error occurred").
            var detail = $"{ex.GetType().Name}: {ex.Message}";
            if (ex.InnerException is not null)
                detail += $" | inner: {ex.InnerException.GetType().Name}: {ex.InnerException.Message}";
            return await Send.ResponseAsync(new { error = detail }, 422, ct);
        }

        return await Send.ResponseAsync(new Void(), (int)HttpStatusCode.NoContent, ct);
    }
}
