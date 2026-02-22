using Dumble.BundleManagementService.Application.Contracts.Repositories;
using Dumble.BundleManagementService.Domain.CategoryAggregate;
using Dumble.BundleManagementService.Domain.CategoryAggregate.ValueObjects;
using MediatR;

namespace Dumble.BundleManagementService.Application.Features.Categories.Queries.GetAllCategories;

public sealed class GetAllCategoriesQueryHandler(IGenericRepository<Category, CategoryId> categoryRepository) : IRequestHandler<GetAllCategoriesQuery, IEnumerable<GetAllCategoriesQueryResponse>>
{
    public async Task<IEnumerable<GetAllCategoriesQueryResponse>> Handle(GetAllCategoriesQuery request, CancellationToken cancellationToken)
    {
        // 1. Get All the Categories from the database
        var categories =  await categoryRepository.GetAll();

        // 2. Map Domain Model to the Desired response form
        var response = categories.Select(c =>
            new GetAllCategoriesQueryResponse(c.Id.Value.ToString(), c.Name.Value));
        
        return response;
    }
}