using Dumble.BundleManagementService.Domain.CategoryAggregate;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Queries.GetAllCategories;

public sealed record GetAllCategoriesQuery() : IRequest<IEnumerable<GetAllCategoriesQueryResponse>>;