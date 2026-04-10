using Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;
using Dumble.BundleManagementService.Contracts.Bundles.GetBundle;
using FastEndpoints;
using MediatR;

namespace Dumble.BundleManagementService.API.Endpoints.Bundles;

public sealed class GetBundleByIdEndpoint(ISender mediator) 
    : Endpoint<GetBundleRequest, GetBundleResponse>
{
    public override void Configure()
    {
        Get("/api/bundles/{id}");
        AllowAnonymous();
        Options(o => o.WithTags("Bundles"));
    }

    public override async Task<GetBundleResponse> ExecuteAsync(GetBundleRequest req, CancellationToken ct)
    {
        var query = new GetBundleQuery(req.Id);

        var result = await mediator.Send(query, ct);

        var response = new GetBundleResponse(
            result.Id.Value,
            result.Images.Select(i => i.Value),
            result.Name.Value,
            result.Description.Value,
            result.Price.Value,
            result.ExpiresOn,
            result.Status.ToString(),
            result.Viewers.Count,
            "Test"
        );

        await Send.OkAsync(response, ct);
        
        return response;
    }
}