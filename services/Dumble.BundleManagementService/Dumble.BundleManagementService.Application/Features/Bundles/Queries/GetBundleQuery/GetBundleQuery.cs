using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;

public sealed record GetBundleQuery(Guid Id, string? ViewerExternalId) : IRequest<GetBundleResult>;

public sealed record GetBundleResult(
    Guid Id,
    IReadOnlyList<string> Images,
    string Name,
    string Description,
    decimal Price,
    DateTime ExpiresOn,
    string Status,
    int ViewCount,
    string CategoryName);
