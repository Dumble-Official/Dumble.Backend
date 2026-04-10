using Dumble.BundleManagementService.Domain.BundleAggregate;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Bundles.Queries.GetBundleQuery;

public sealed record GetBundleQuery(Guid Id) : IRequest<Bundle>;